package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.config.EurobondEvdsProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EurobondInstrumentRegistryTest {

    @Test
    void resolvesConfiguredInstruments() {
        EurobondEvdsProperties props = new EurobondEvdsProperties();
        EurobondEvdsProperties.Instrument ins = new EurobondEvdsProperties.Instrument();
        ins.setIsin("US900123AT75");
        ins.setName("Test");
        ins.setCouponPct(new BigDecimal("8"));
        ins.setMaturityDate("2034-02-14");
        props.getInstruments().setList(List.of(ins));

        EurobondInstrumentRegistry registry = new EurobondInstrumentRegistry(props);
        assertThat(registry.all()).hasSize(1);
        assertThat(registry.findByIsin("us900123at75")).isPresent();
    }
}
