package ai.motoria.listing.dto;

import ai.motoria.listing.domain.Fuel;
import ai.motoria.listing.domain.ListingSort;
import ai.motoria.listing.domain.Province;
import ai.motoria.listing.domain.Transmission;
import ai.motoria.listing.domain.TrustLevel;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import java.math.BigDecimal;

/**
 * Public search filters (design D9). Only PUBLISHED listings are searched.
 */
public class ListingSearchRequest {

    @QueryParam("brand")
    public String brand;

    @QueryParam("model")
    public String model;

    @QueryParam("yearFrom")
    public Integer yearFrom;

    @QueryParam("yearTo")
    public Integer yearTo;

    @QueryParam("minPrice")
    public BigDecimal minPrice;

    @QueryParam("maxPrice")
    public BigDecimal maxPrice;

    @QueryParam("maxMileage")
    public Integer maxMileage;

    @QueryParam("province")
    public Province province;

    @QueryParam("fuel")
    public Fuel fuel;

    @QueryParam("transmission")
    public Transmission transmission;

    @QueryParam("trustLevel")
    public TrustLevel trustLevel;

    @QueryParam("sort")
    @DefaultValue("RELEVANCE")
    public ListingSort sort;

    @QueryParam("page")
    @DefaultValue("0")
    public int page;

    @QueryParam("size")
    @DefaultValue("20")
    public int size;
}
