package com.lims.printing.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.printing.model.PrintJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PrintAgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PrintAgentWebSocketHandler.class);
    private static final int RECENT_JOB_BUFFER = 100;
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 16 * 1024 * 1024;

    /**
     * Sessions are wrapped in {@link ConcurrentWebSocketSessionDecorator}: print jobs are dispatched
     * from async listener threads, and concurrent sends on a raw session fail with TEXT_PARTIAL_WRITING.
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Deque<PrintJob> recentSpoolJobs = new ArrayDeque<>();
    private final ObjectMapper objectMapper;

    public PrintAgentWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        sessions.put(session.getId(), safeSession);
        log.info("Print bridge agent connected via WebSocket: {} as {} (Total agents connected: {})",
                session.getId(), session.getAttributes().get(PrintAgentHandshakeInterceptor.USER_ATTRIBUTE), sessions.size());

        safeSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                new HandshakeMessage("PRINT_AGENT_CONNECTED", "Apex LIMS Zero-Click Spooler Ready", sessions.size())
        )));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("Print bridge agent disconnected: {} (Remaining agents: {})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("Received print bridge ACK/Status from {} ({} chars)", session.getId(), message.getPayloadLength());
    }

    public void dispatchPrintJob(PrintJob job) {
        synchronized (recentSpoolJobs) {
            recentSpoolJobs.addFirst(job);
            while (recentSpoolJobs.size() > RECENT_JOB_BUFFER) {
                recentSpoolJobs.removeLast();
            }
        }

        log.info("Spooling silent print job {} [Target: {}] for order {}", job.jobId(), job.target(), job.accessionNumber());

        if (sessions.isEmpty()) {
            log.warn("No active hardware print agents connected on /ws/print-agent. Job {} kept in memory spooler.", job.jobId());
            return;
        }

        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(job));
        } catch (IOException e) {
            log.error("Failed to serialize print job {}: {}", job.jobId(), e.getMessage(), e);
            return;
        }

        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(message);
                log.debug("Dispatched print job {} to agent session {}", job.jobId(), session.getId());
            } catch (IOException | IllegalStateException e) {
                // One broken agent must not stop delivery to the others
                log.warn("Failed to send print job {} to agent {}: {}", job.jobId(), session.getId(), e.getMessage());
            }
        }
    }

    public List<PrintJob> getRecentSpoolJobs() {
        synchronized (recentSpoolJobs) {
            return List.copyOf(recentSpoolJobs);
        }
    }

    public int getConnectedAgentCount() {
        return sessions.size();
    }

    public record HandshakeMessage(String type, String message, int activeAgents) {}
}
