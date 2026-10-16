package com.lims.diagnostics.service;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.service.CatalogService;
import com.lims.common.api.ClinicalOrderGateway;
import com.lims.common.api.OrderSnapshot;
import com.lims.common.enums.OrderStatus;
import com.lims.common.event.ResultEvaluatedEvent;
import com.lims.common.exception.InvalidOperationException;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lims.diagnostics.entity.OrderAuthorization;
import com.lims.diagnostics.entity.TestResult;
import com.lims.diagnostics.repository.OrderAuthorizationRepository;
import com.lims.diagnostics.repository.TestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ResultEntryService {

    private static final Logger log = LoggerFactory.getLogger(ResultEntryService.class);
    private static final Set<OrderStatus> PRE_ANALYTICAL = EnumSet.of(OrderStatus.REGISTERED, OrderStatus.COLLECTED, OrderStatus.PROCESSING);

    private final ClinicalOrderGateway orderGateway;
    private final TestResultRepository testResultRepository;
    private final OrderAuthorizationRepository orderAuthorizationRepository;
    private final CriticalRangeEvaluator criticalRangeEvaluator;
    private final DeltaCheckEngine deltaCheckEngine;
    private final CatalogService catalogService;
    private final ApplicationEventPublisher eventPublisher;

    public ResultEntryService(ClinicalOrderGateway orderGateway,
                              TestResultRepository testResultRepository,
                              OrderAuthorizationRepository orderAuthorizationRepository,
                              CriticalRangeEvaluator criticalRangeEvaluator,
                              DeltaCheckEngine deltaCheckEngine,
                              CatalogService catalogService,
                              ApplicationEventPublisher eventPublisher) {
        this.orderGateway = orderGateway;
        this.testResultRepository = testResultRepository;
        this.orderAuthorizationRepository = orderAuthorizationRepository;
        this.criticalRangeEvaluator = criticalRangeEvaluator;
        this.deltaCheckEngine = deltaCheckEngine;
        this.catalogService = catalogService;
        this.eventPublisher = eventPublisher;
    }

    public List<ResultEntryDtos.TestResultDto> saveResults(ResultEntryDtos.BatchResultEntryRequest request) {
        OrderSnapshot order = orderGateway.getOrder(request.orderId());

        // Signed reports are immutable: changing a value after sign-off would invalidate the signature
        if (order.status() == OrderStatus.AUTHORIZED || orderAuthorizationRepository.findByOrderId(order.orderId()).isPresent()) {
            throw new InvalidOperationException("Order " + order.accessionNumber()
                    + " has been authorized; its results are locked and can no longer be edited.");
        }

        List<ResultEntryDtos.TestResultDto> savedResults = new ArrayList<>();

        for (ResultEntryDtos.ParameterResultInput input : request.results()) {
            String observed = input.observedValue().trim();
            TestResult result = testResultRepository.findByOrderIdAndParameterId(order.orderId(), input.parameterId())
                    .orElseGet(() -> new TestResult(
                            UUID.randomUUID(),
                            order.orderId(),
                            input.parameterId(),
                            input.parameterName(),
                            input.unit(),
                            observed
                    ));

            result.setObservedValue(observed);
            result.setUnit(input.unit());
            result.setParameterName(input.parameterName());
            result.setTechnicianNotes(input.technicianNotes() != null && !input.technicianNotes().isBlank()
                    ? input.technicianNotes().trim() : null);

            // 1. Evaluate critical / out-of-range thresholds based on age and gender
            CriticalRangeEvaluator.EvaluationResult eval = criticalRangeEvaluator.evaluate(
                    input.parameterId(), observed, order.patientAge(), order.patientGender()
            );
            result.setIsOutOfRange(eval.isOutOfRange());
            result.setIsCritical(eval.isCritical());

            // 2. Delta check against the patient's most recent prior result for this analyte
            DeltaCheckEngine.DeltaCheckResult delta = deltaCheckEngine.evaluateDelta(
                    order.patientId(), order.orderId(), input.parameterId(), observed
            );
            result.setRequiresReview(delta.requiresReview());
            result.setDeltaChangePercentage(delta.deltaChangePercentage());
            result.setEvaluatedAt(Instant.now());

            TestResult saved = testResultRepository.save(result);

            eventPublisher.publishEvent(new ResultEvaluatedEvent(
                    saved.getId(),
                    saved.getOrderId(),
                    saved.getParameterId(),
                    saved.getParameterName(),
                    saved.getObservedValue(),
                    saved.getIsCritical(),
                    saved.getIsOutOfRange(),
                    saved.getRequiresReview(),
                    saved.getDeltaChangePercentage(),
                    Instant.now()
            ));

            savedResults.add(toDto(saved, eval.normalLow(), eval.normalHigh(), eval.criticalLow(), eval.criticalHigh(), eval.textNormalVal()));
        }

        if (PRE_ANALYTICAL.contains(order.status())) {
            orderGateway.updateStatus(order.orderId(), OrderStatus.COMPLETED);
            log.info("Order {} transitioned to COMPLETED status after results entry", order.accessionNumber());
        }

        return savedResults;
    }

    @Transactional(readOnly = true)
    public ResultEntryDtos.OrderWithResultsDto getOrderResultsWithDemographics(UUID orderId) {
        OrderSnapshot order = orderGateway.getOrder(orderId);

        List<ResultEntryDtos.TestResultDto> resultDtos = testResultRepository.findByOrderId(orderId).stream().map(r -> {
            Optional<TestCatalogDto.ReferenceRangeDto> range = catalogService.getReferenceRangeForParameter(
                    r.getParameterId(), order.patientAge(), order.patientGender()
            );
            return toDto(r,
                    range.map(TestCatalogDto.ReferenceRangeDto::normalLow).orElse(null),
                    range.map(TestCatalogDto.ReferenceRangeDto::normalHigh).orElse(null),
                    range.map(TestCatalogDto.ReferenceRangeDto::criticalLow).orElse(null),
                    range.map(TestCatalogDto.ReferenceRangeDto::criticalHigh).orElse(null),
                    range.map(TestCatalogDto.ReferenceRangeDto::textNormalVal).orElse(null));
        }).toList();

        ResultEntryDtos.OrderWithResultsDto.AuthorizationInfoDto authDto = orderAuthorizationRepository.findByOrderId(orderId)
                .map(ResultEntryService::toAuthorizationInfo)
                .orElse(null);

        return new ResultEntryDtos.OrderWithResultsDto(
                order.orderId(),
                order.accessionNumber(),
                order.patientUhid(),
                order.patientFullName(),
                order.patientAge(),
                order.patientGender().name(),
                order.referringDoctor(),
                order.status().name(),
                resultDtos,
                authDto
        );
    }

    private static ResultEntryDtos.OrderWithResultsDto.AuthorizationInfoDto toAuthorizationInfo(OrderAuthorization a) {
        return new ResultEntryDtos.OrderWithResultsDto.AuthorizationInfoDto(
                a.getPathologistName(),
                a.getLicenseNumber(),
                a.getDigitalSignatureHash(),
                a.getVerificationToken(),
                a.getAuthorizedBy(),
                a.getAuthorizedAt()
        );
    }

    private static ResultEntryDtos.TestResultDto toDto(TestResult r, java.math.BigDecimal normalLow, java.math.BigDecimal normalHigh,
                                                       java.math.BigDecimal criticalLow, java.math.BigDecimal criticalHigh,
                                                       String textNormalVal) {
        return new ResultEntryDtos.TestResultDto(
                r.getId(),
                r.getOrderId(),
                r.getParameterId(),
                r.getParameterName(),
                r.getUnit(),
                r.getObservedValue(),
                r.getIsCritical(),
                r.getIsOutOfRange(),
                r.getRequiresReview(),
                r.getDeltaChangePercentage(),
                r.getTechnicianNotes(),
                normalLow,
                normalHigh,
                criticalLow,
                criticalHigh,
                textNormalVal,
                r.getEnteredAt()
        );
    }
}
