package io.txbox.jpa;

import java.util.List;

@FunctionalInterface
public interface TxBoxJpaPackagesCustomizer {
    List<String> customize();
}
