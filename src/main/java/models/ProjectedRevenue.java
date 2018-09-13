package models;

import j2html.tags.ContainerTag;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public class ProjectedRevenue extends Model {

    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.VALUE,
            Constants.YEAR,
            Constants.NOTES,
            Constants.SOURCE,
            Constants.IS_ESTIMATE,
            Constants.ESTIMATE_TYPE,
            Constants.CAGR,
            Constants.COMPANY_ID,
            Constants.MARKET_ID,
            Constants.REGION_ID,
            Constants.PARENT_REVENUE_ID,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public ProjectedRevenue(Map<String,Object> data) {
        super(Collections.emptyList(), ATTRS, "projected_revenues", null, data, true);
    }

    @Override
    public String getName() {
        return "Projection for "+data.get(Constants.YEAR);
    }

    @Override
    public ContainerTag getSimpleLink(String... additionalClasses) {
        return div().with(
                div(getName()), getRevenueAsSpan())
        )
        return div(getCalculationInformationInfoDiv(calculationInformation)).attr("style", "list-style: none;");
    }


    @Override
    public ContainerTag getLink(@NonNull String associationName, @NonNull String associationModel, @NonNull Integer associationId) {
        if(data==null) {
            loadAttributesFromDatabase();
        }
        return div().with(
                getSimpleLink()
        );
    }

    @Override
    public void createInDatabase() {
        throw new RuntimeException("Unable to create database record for projected revenues.");
    }

    private ContainerTag getCalculationInformationInfoDiv(List<CalculationInformation> information) {
        if(information!=null) {
            // check for CAGR's used
            ContainerTag ul = span();
            for(CalculationInformation info : information.stream().filter(c->c.getYear()!=null).sorted((c1,c2)->Integer.compare(c2.getYear(),c1.getYear())).collect(Collectors.toList())) {
                if (info.getCagrUsed() != null && info.getRevenue() != null) {
                    // found cagr
                    boolean estimated = info.getReference().getData().get(Constants.CAGR)==null;
                    ul.with(
                           .attr("style", "font-weight: bold; cursor: pointer;").withClass("resource-data-field").attr("onclick", "$(this).children().slideToggle();").attr("data-val", info.getRevenue().toString()).with(
                                    div().attr("style", "display: none;").with(
                                            div("CAGR used: " + Constants.getFieldFormatter(Constants.CAGR).apply(info.getCagrUsed()) + (estimated ? " (Estimated)" : "")).attr("style", "font-weight: normal;"),
                                            div("From revenue: ").with(info.getReference().getSimpleLink().attr("style", "display: inline;")).attr("style", "font-weight: normal;")
                                    )
                            )
                    );
                }
            }
            return ul;
        }
        return null;
    }


}
