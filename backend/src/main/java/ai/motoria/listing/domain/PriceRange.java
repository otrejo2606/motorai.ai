package ai.motoria.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class PriceRange {

    @Column(name = "price_min", nullable = false)
    private BigDecimal min;

    @Column(name = "price_max", nullable = false)
    private BigDecimal max;

    public PriceRange() {
    }

    public PriceRange(BigDecimal min, BigDecimal max) {
        this.min = min;
        this.max = max;
    }

    public BigDecimal getMin() {
        return min;
    }

    public BigDecimal getMax() {
        return max;
    }
}
