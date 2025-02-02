package uk.ac.ebi.biosamples.service;

import org.springframework.stereotype.Component;
import uk.ac.ebi.biosamples.model.ga4gh.Ga4ghLocation;

import java.util.Scanner;

/**
 * GEolocationDataHelper is util class for working with ga4gh sample location
 *
 * @author Dilshat Salikhov
 */
@Component
public class GeoLocationDataHelper {

    public boolean isGeoLocationData(String type) {
        boolean isGeolocation = type.contains("geographic location");
        isGeolocation = isGeolocation || type.contains("location");
        isGeolocation = isGeolocation || type.contains("latitude");
        isGeolocation = isGeolocation || type.contains("longitude");
        isGeolocation = isGeolocation || type.contains("altitude");
        isGeolocation = isGeolocation || type.contains("precision");
        return isGeolocation;
    }

    public Ga4ghLocation convertToDecimalDegree(String location) {
        int nsCoef = 1;
        if (location.contains("S")) {
            nsCoef = -1;
        }
        int weCoef = 1;
        if (location.contains("W")) {
            weCoef = -1;
        }

        Scanner scanner = new Scanner(location);
        double latitude = scanner.nextDouble();
        while (!scanner.hasNextDouble()) {
            scanner.next();
        }
        double longtitude = scanner.nextDouble();
        return new Ga4ghLocation(latitude * nsCoef, longtitude * weCoef);

    }
}
