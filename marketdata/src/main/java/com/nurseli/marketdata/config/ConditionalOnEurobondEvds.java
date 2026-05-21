package com.nurseli.marketdata.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Eurobond EVDS / enstrüman modülü — {@code app.market.eurobonds.evds.enabled=true} olmadan bean yüklenmez. */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "app.market.eurobonds.evds", name = "enabled", havingValue = "true", matchIfMissing = false)
public @interface ConditionalOnEurobondEvds {}
