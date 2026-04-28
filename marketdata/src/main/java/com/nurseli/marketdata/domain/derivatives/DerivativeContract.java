package com.nurseli.marketdata.domain.derivatives;

import jakarta.persistence.*;

@Entity
@Table(name = "derivative_contract")
public class DerivativeContract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String contractCode;

    @Column(nullable = false, length = 32)
    private String underlying;

    @Column(nullable = false, length = 16)
    private String expiry;

    @Column(nullable = false, length = 16)
    private String type;

    public Long getId() { return id; }
    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }
    public String getUnderlying() { return underlying; }
    public void setUnderlying(String underlying) { this.underlying = underlying; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
