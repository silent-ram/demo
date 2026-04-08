package com.example.deviceservice.controller;

import com.example.deviceservice.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/report")
@Tag(name = "报表导出", description = "导出各类报表")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/export/device")
    @Operation(summary = "导出设备台账", description = "导出设备台账Excel报表")
    public ResponseEntity<byte[]> exportDeviceReport() {
        byte[] excelData = reportService.exportDeviceReport();

        String fileName = "设备台账_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return new ResponseEntity<>(excelData, headers, HttpStatus.OK);
    }
}