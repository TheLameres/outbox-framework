package io.txbox.core.event;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainEventScannerTest {

    @Test
    void scan_fixtures() {
        var domainEventScanner = new DomainEventScanner(List.of("com.somecompany.project.fixture"), this.getClass().getClassLoader());
        var scanned = domainEventScanner.scan();
        assertNotNull(scanned);
        assertEquals(3, scanned.size());
    }

    @Test
    void scan_whenNoAnnotation() {
        var domainEventScanner = new DomainEventScanner(List.of("com.somecompany.project.no_annotation"), this.getClass().getClassLoader());
        var scanned = domainEventScanner.scan();
        assertNotNull(scanned);
        assertEquals(0, scanned.size());
    }
}