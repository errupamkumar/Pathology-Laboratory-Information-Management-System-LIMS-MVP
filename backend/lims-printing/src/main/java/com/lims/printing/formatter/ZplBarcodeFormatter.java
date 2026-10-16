package com.lims.printing.formatter;

import org.springframework.stereotype.Component;

@Component
public class ZplBarcodeFormatter {

    /**
     * Generates a 2" x 1" (approx 400x200 dots @ 203 DPI) vacutainer ZPL label.
     */
    public String generateZpl(String uhid, String accessionNumber, String patientName,
                              String tubeType, String specimenType, String barcode) {
        StringBuilder sb = new StringBuilder();
        sb.append("^XA\n");
        sb.append("^PW420\n");
        sb.append("^LL220\n");
        sb.append("^LH10,10\n");

        // Header: Lab brand & Tube container
        sb.append("^FO10,10^A0N,22,22^FDAPEX PATH LAB - ").append(tubeType).append("^FS\n");

        // Patient UHID and Name
        sb.append("^FO10,38^A0N,20,20^FD").append(uhid).append("  ").append(patientName).append("^FS\n");

        // Code 128 Barcode
        sb.append("^BY2,2.5,65\n");
        sb.append("^FO15,65^BCN,65,Y,N,N\n");
        sb.append("^FD").append(barcode).append("^FS\n");

        // Footer: Accession number & specimen type
        sb.append("^FO10,165^A0N,20,20^FDACC: ").append(accessionNumber)
                .append(" (").append(specimenType).append(")^FS\n");

        sb.append("^XZ\n");
        return sb.toString();
    }
}
