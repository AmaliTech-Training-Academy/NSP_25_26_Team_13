package com.logstream.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class LogEntryCSV {

    @CsvBindByName(column = "service_name")
    private String serviceName;

    @CsvBindByName(column = "level")
    private String level;

    @CsvBindByName(column = "message")
    private String message;

    @CsvBindByName(column = "source")
    private String source;

    @CsvBindByName(column = "metadata")
    private String metadata;

    @CsvBindByName(column = "timestamp")
    private String timestamp;

}