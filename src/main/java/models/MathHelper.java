package models;

import java.util.List;

public class MathHelper {
    public static double calculateCagr(List<Double> revenues) {
        return 100.0 * (Math.pow(revenues.get(revenues.size()-1)/revenues.get(0), 1.0/(revenues.size()-1)) - 1d);
    }
}
