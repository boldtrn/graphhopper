/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.dem;

import com.graphhopper.storage.DataAccess;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One rectangle of height data from Shuttle Radar Topography Mission.
 * <p>
 *
 * @author Peter Karich
 */
public class HeightTile {
    private final int minLat;
    private final int minLon;
    private final int width;
    private final int degree;
    private final double lowerBound;
    private final double higherBound;
    private DataAccess heights;
    private boolean calcMean;
    private boolean bilinearInterpolation = false;

    public HeightTile(int minLat, int minLon, int width, double precision, int degree) {
        this.minLat = minLat;
        this.minLon = minLon;
        this.width = width;

        this.lowerBound = -1 / precision;
        this.higherBound = degree + 1 / precision;

        this.degree = degree;
    }

    public HeightTile setCalcMean(boolean b) {
        this.calcMean = b;
        return this;
    }

    public boolean isSeaLevel() {
        return heights.getHeader(0) == 1;
    }

    public HeightTile setSeaLevel(boolean b) {
        heights.setHeader(0, b ? 1 : 0);
        return this;
    }

    void setHeights(DataAccess da) {
        this.heights = da;
    }

    public double getHeight(double lat, double lon) {
        double deltaLat = Math.abs(lat - minLat);
        double deltaLon = Math.abs(lon - minLon);
        if (deltaLat > higherBound || deltaLat < lowerBound)
            throw new IllegalStateException("latitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString());
        if (deltaLon > higherBound || deltaLon < lowerBound)
            throw new IllegalStateException("longitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString());

        // first row in the file is the northernmost one
        // http://gis.stackexchange.com/a/43756/9006
        int lonSimilar = (int) ((width / degree) * deltaLon);
        // different fallback methods for lat and lon as we have different rounding (lon -> positive, lat -> negative)
        if (lonSimilar >= width)
            lonSimilar = width - 1;
        int latSimilar = width - 1 - (int) ((width / degree) * deltaLat);
        if (latSimilar < 0)
            latSimilar = 0;

        // always keep in mind factor 2 because of short value
        int daPointer = 2 * (latSimilar * width + lonSimilar);
        int value = heights.getShort(daPointer);
        AtomicInteger counter = new AtomicInteger(1);
        if (value == Short.MIN_VALUE)
            return Double.NaN;


        double doubleLonSimilar = (width / degree) * deltaLon;
        if (doubleLonSimilar >= width)
            doubleLonSimilar = width - 1;
        double doubleLatSimilar = width - 1 - ((width / degree) * deltaLat);
        if (doubleLatSimilar < 0)
            doubleLatSimilar = 0;

        double cellSize = ((double) degree) / width;
        double horizontalDistFromBorder = doubleLonSimilar % cellSize;
        double verticalDistFromBorder = doubleLatSimilar % cellSize;

        double x1, x2, y1, y2;

        double tl;
        double tr;
        double bl;
        double br;

        // Point lies to the left
        if (horizontalDistFromBorder < .5 * cellSize) {
            x1 = (lonSimilar - 1) * cellSize;
            x2 = (lonSimilar) * cellSize;
            // Point lies in the upper half
            if (verticalDistFromBorder < .5 * cellSize) {
                tl = getUpperLeftValue(daPointer, latSimilar, lonSimilar, value);
                tr = getUpperValue(daPointer, latSimilar, value);
                bl = getLeftValue(daPointer, lonSimilar, value);
                br = value;

                y1 = latSimilar * cellSize;
                y2 = (latSimilar - 1) * cellSize;
            } else {
                tl = getLeftValue(daPointer, lonSimilar, value);
                tr = value;
                bl = getLowerLeftValue(daPointer, latSimilar, lonSimilar, value);
                br = getLowerValue(daPointer, latSimilar, value);

                y1 = (latSimilar + 1) * cellSize;
                y2 = latSimilar * cellSize;
            }
            // Point lies to the right
        } else {
            x1 = (lonSimilar) * cellSize;
            x2 = (lonSimilar + 1) * cellSize;

            // Point lies in the upper half
            if (verticalDistFromBorder < .5 * cellSize) {
                tl = getUpperValue(daPointer, latSimilar, value);
                tr = getUpperRightValue(daPointer, latSimilar, lonSimilar, value);
                bl = value;
                br = getRightValue(daPointer, lonSimilar, value);

                y1 = latSimilar * cellSize;
                y2 = (latSimilar - 1) * cellSize;
            } else {
                tl = value;
                tr = getRightValue(daPointer, lonSimilar, value);
                bl = getLowerValue(daPointer, latSimilar, value);
                br = getLowerRightValue(daPointer, latSimilar, lonSimilar, value);

                y1 = (latSimilar + 1) * cellSize;
                y2 = latSimilar * cellSize;
            }
        }

        System.out.println("Orig: " + value);
        double interpolated = biLerp(doubleLonSimilar, doubleLatSimilar, bl, tl, br, tr, x1, x2, y1, y2);
        System.out.println("Int:" + interpolated);
        return interpolated;

        //if (bilinearInterpolation)
        //return getInterpolatedValue(value, deltaLat, deltaLon, latSimilar, lonSimilar, daPointer);
/*
        if (calcMean) {
            if (lonSimilar > 0)
                value += includePoint(daPointer - 2, counter);

            if (lonSimilar < width - 1)
                value += includePoint(daPointer + 2, counter);

            if (latSimilar > 0)
                value += includePoint(daPointer - 2 * width, counter);

            if (latSimilar < width - 1)
                value += includePoint(daPointer + 2 * width, counter);
        }

        return (double) value / counter.get();
        */
    }

    public static double lerp(double x, double x1, double x2, double q00, double q01) {
        return ((x2 - x) / (x2 - x1)) * q00 + ((x - x1) / (x2 - x1)) * q01;
    }

    public static double biLerp(double x, double y, double q11, double q12, double q21, double q22, double x1, double x2, double y1, double y2) {
        double r1 = lerp(x, x1, x2, q11, q21);
        double r2 = lerp(x, x1, x2, q12, q22);

        return lerp(y, y1, y2, r1, r2);
    }

    private double getInterpolatedValue(int value, double deltaLat, double deltaLon, int latSimilar, int lonSimilar, int daPointer) {
        double interpolatedValue = value;
        double doubleLonSimilar = (width / degree) * deltaLon;
        if (doubleLonSimilar >= width)
            doubleLonSimilar = width - 1;
        double doubleLatSimilar = (width / degree) * deltaLat;
        if (doubleLatSimilar < 0)
            doubleLatSimilar = 0;

        double cellSize = ((double) degree) / width;
        double horizontalDistFromBorder = doubleLonSimilar % cellSize;
        double verticalDistFromBorder = doubleLatSimilar % cellSize;

        List<Double> list = new ArrayList<>(4);
        list.add(interpolatedValue);

        if (horizontalDistFromBorder < cellSize * .1 && verticalDistFromBorder < cellSize * .1) {
            list.add(getUpperLeftValue(daPointer, latSimilar, lonSimilar, interpolatedValue));
        } else if (horizontalDistFromBorder > cellSize * .9 && verticalDistFromBorder < cellSize * .1) {
            list.add(getUpperRightValue(daPointer, latSimilar, lonSimilar, interpolatedValue));
        } else if (horizontalDistFromBorder < cellSize * .1 && verticalDistFromBorder > cellSize * .9) {
            list.add(getLowerLeftValue(daPointer, latSimilar, lonSimilar, interpolatedValue));
        } else if (horizontalDistFromBorder > cellSize * .9 && verticalDistFromBorder > cellSize * .9) {
            list.add(getLowerRightValue(daPointer, latSimilar, lonSimilar, interpolatedValue));
        }

        if (horizontalDistFromBorder < cellSize * .25) {
            list.add(getLeftValue(daPointer, lonSimilar, interpolatedValue));
        } else if (horizontalDistFromBorder > cellSize * .75) {
            list.add(getRightValue(daPointer, lonSimilar, interpolatedValue));
        }
        if (verticalDistFromBorder < cellSize * .25) {
            list.add(getUpperValue(daPointer, latSimilar, interpolatedValue));
        } else if (verticalDistFromBorder > cellSize * .75) {
            list.add(getLowerValue(daPointer, latSimilar, interpolatedValue));
        }

        double interpolated = value;
        for (Double d : list) {
            interpolated += d;
        }

        return interpolated / (list.size() + 1);
    }

    private double getLeftValue(int daPointer, int lonSimilar, double fallbackValue) {
        if (lonSimilar > 0) {
            // TODO: Hanlde Short.min
            return heights.getShort(daPointer - 2);
        }
        return fallbackValue;
    }

    private double getRightValue(int daPointer, int lonSimilar, double fallbackValue) {
        if (lonSimilar < width - 1) {
            // TODO: Hanlde Short.min
            return heights.getShort(daPointer + 2);
        }
        return fallbackValue;
    }

    private double getUpperValue(int daPointer, int latSimilar, double fallbackValue) {
        if (latSimilar > 0) {
            return heights.getShort(daPointer - 2 * width);
        }
        return fallbackValue;
    }

    private double getLowerValue(int daPointer, int latSimilar, double fallbackValue) {
        if (latSimilar < width - 1) {
            return heights.getShort(daPointer + 2 * width);
        }
        return fallbackValue;
    }

    private double getUpperLeftValue(int daPointer, int latSimilar, int lonSimilar, double fallbackValue) {
        if (latSimilar > 0 && lonSimilar > 0) {
            return heights.getShort(daPointer - (2 * width) - 2);
        }
        return fallbackValue;
    }

    private double getUpperRightValue(int daPointer, int latSimilar, int lonSimilar, double fallbackValue) {
        if (latSimilar > 0 && lonSimilar < width - 1) {
            return heights.getShort(daPointer - (2 * width) + 2);
        }
        return fallbackValue;
    }

    private double getLowerLeftValue(int daPointer, int latSimilar, int lonSimilar, double fallbackValue) {
        if (latSimilar < width - 1 && lonSimilar > 0) {
            return heights.getShort(daPointer + (2 * width) - 2);
        }
        return fallbackValue;
    }

    private double getLowerRightValue(int daPointer, int latSimilar, int lonSimilar, double fallbackValue) {
        if (latSimilar < width - 1 && lonSimilar < width - 1) {
            return heights.getShort(daPointer + (2 * width) + 2);
        }
        return fallbackValue;
    }

    private double includePoint(int pointer, AtomicInteger counter) {
        short value = heights.getShort(pointer);
        if (value == Short.MIN_VALUE)
            return 0;

        counter.incrementAndGet();
        return value;
    }

    public void toImage(String imageFile) throws IOException {
        ImageIO.write(makeARGB(), "PNG", new File(imageFile));
    }

    protected BufferedImage makeARGB() {
        int height = width;
        BufferedImage argbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = argbImage.getGraphics();
        long len = width * width;
        for (int i = 0; i < len; i++) {
            int lonSimilar = i % width;
            // no need for width - y as coordinate system for Graphics is already this way
            int latSimilar = i / width;
            int green = Math.abs(heights.getShort(i * 2));
            if (green == 0) {
                g.setColor(new Color(255, 0, 0, 255));
            } else {
                int red = 0;
                while (green > 255) {
                    green = green / 10;
                    red += 50;
                }
                if (red > 255)
                    red = 255;
                g.setColor(new Color(red, green, 122, 255));
            }
            g.drawLine(lonSimilar, latSimilar, lonSimilar, latSimilar);
        }
        g.dispose();
        return argbImage;
    }

    public BufferedImage getImageFromArray(int[] pixels, int width) {
        int height = width;
        BufferedImage tmpImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        tmpImage.setRGB(0, 0, width, height, pixels, 0, width);
        return tmpImage;
    }

    @Override
    public String toString() {
        return minLat + "," + minLon;
    }

    public void setBilinearInterpolation(boolean bilinearInterpolation) {
        this.bilinearInterpolation = bilinearInterpolation;
    }
}
