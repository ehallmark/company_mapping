package models;

import java.util.ArrayList;
import java.util.List;

public class MathHelper {
    public static double calculateCagr(List<Double> revenues) {
        return calculateCagr(revenues.get(revenues.size() - 1), revenues.get(0), revenues.size() - 1);
    }

    public static double calculateCagr(double endRev, double startRev, int t) {
        return 100.0 * (Math.pow(endRev/startRev, 1.0/t) - 1d);
    }


    public static Double calculateCagrFromModels(List<Model> list) {
        if(list==null||list.size()<2) return null;
        list = new ArrayList<>(list);
        list.sort((e1,e2)->Integer.compare((Integer)e1.getData().get(Constants.YEAR) , (Integer)e2.getData().get(Constants.YEAR)));
        int minYear = (Integer)list.get(0).getData().get(Constants.YEAR);
        int maxYear = (Integer)list.get(list.size()-1).getData().get(Constants.YEAR);
        if(minYear==maxYear) return null;
        return calculateCagr((Double)list.get(list.size()-1).getData().get(Constants.VALUE), (Double)list.get(0).getData().get(Constants.VALUE), maxYear - minYear);
    }
}
