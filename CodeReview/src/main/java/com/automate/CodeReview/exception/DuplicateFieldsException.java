package com.automate.CodeReview.exception;

import lombok.Getter;
import java.util.List;

@Getter
public class DuplicateFieldsException extends RuntimeException {
    private final List<String> duplicateFields;

    public DuplicateFieldsException(List<String> duplicateFields) {
        super("Duplicate fields: " + String.join(", ", duplicateFields));
        this.duplicateFields = duplicateFields;
    }
}