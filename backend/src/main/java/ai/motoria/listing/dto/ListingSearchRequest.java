package ai.motoria.listing.dto;

import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.common.domain.ListingStatus;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import java.math.BigDecimal;

public class ListingSearchRequest {

    @QueryParam("status")
    public ListingStatus status;

    @QueryParam("category")
    public ListingCategory category;

    @QueryParam("minPrice")
    public BigDecimal minPrice;

    @QueryParam("maxPrice")
    public BigDecimal maxPrice;

    @QueryParam("modelYear")
    public Integer modelYear;

    @QueryParam("page")
    @DefaultValue("0")
    public int page;

    @QueryParam("size")
    @DefaultValue("20")
    public int size;
}