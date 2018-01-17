package com.graphhopper.api;

import com.graphhopper.api.model.GHGeocodingRequest;
import com.graphhopper.api.model.GHGeocodingResponse;
import org.junit.Before;
import org.junit.Test;

import java.net.SocketTimeoutException;

import static com.graphhopper.api.GraphHopperWebIT.KEY;
import static org.junit.Assert.*;

/**
 * @author Robin Boldt
 */
public class GraphHopperGeocodingIT {

    GraphHopperGeocoding geocoding = new GraphHopperGeocoding();

    @Before
    public void setUp() {
        String key = System.getProperty("graphhopper.key", KEY);
        geocoding.setKey(key);
    }

    @Test
    public void testForwardGeocoding() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest("Berlin", "en", 7));
        assertEquals(7, response.getHits().size());
        assertTrue(response.getHits().get(0).getName().contains("Berlin"));
    }

    @Test
    public void testForwardGeocodingNominatim() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest(false, Double.NaN, Double.NaN, "Berlin", "en", 5, "nominatim", 5000));
        assertEquals(5, response.getHits().size());
        assertTrue(response.getHits().get(0).getName().contains("Berlin"));
    }

    @Test
    public void testReverseGeocoding() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest(52.5170365, 13.3888599, "en", 5));
        assertEquals(5, response.getHits().size());
        assertTrue(response.getHits().get(0).getName().contains("Berlin"));
    }

    @Test
    public void testTimeout() {
        try {
            // We set the timeout to 1ms, it shouldn't be possible for the API to answer that quickly => we will receive a SocketTimeout
            geocoding.geocode(new GHGeocodingRequest(false, Double.NaN, Double.NaN, "Berlin", "en", 5, "default", 1));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                return;
            }
        }
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForwardException() {
        geocoding.geocode(new GHGeocodingRequest(false, 1, 1, null, "en", 5, "default", 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBackwadException() {
        geocoding.geocode(new GHGeocodingRequest(true, Double.NaN, Double.NaN, "Berlin", "en", 5, "default", 1));
    }

}
