package com.graphhopper.util;

import java.util.*;

/**
 * Scan-Line algorithm to fill a complex polygon.
 * For general consideration see: 
 * http://www-lehre.informatik.uni-osnabrueck.de/~cg/2000/skript/4_2_Scan_Line_Verfahren_f_252_r.html
 * Basic usage; (1) addEdge (2) finalizeAllEdgesTable (3) doScanLineFill
 *
 * @author jansoe
 */
public class ScanLinePolyFill
{

    private LinkedList<ScanlineEdge> allEdgesTable = new LinkedList<ScanlineEdge>();
    private TreeSet<ScanlineEdge> activeEdges = new TreeSet<ScanlineEdge>(new Comparator<ScanlineEdge>()
    {
        @Override
        public int compare( ScanlineEdge scanlineEdge1, ScanlineEdge scanlineEdge2 )
        {
            int comparison = Double.compare(scanlineEdge1.xAtscanlineY, scanlineEdge2.xAtscanlineY);
            if (comparison != 0)
            {
                return comparison;
            } else
            {
                return Double.compare(scanlineEdge1.slopeInverse, scanlineEdge2.slopeInverse);
            }
        }
    });
    private boolean allEdgesTableCompleted = false;
    private double globalYmin = Double.MAX_VALUE;
    private SpatialPixelMap spatialPixelMap;
    private byte value = 0;

    public ScanLinePolyFill( SpatialPixelMap spatialPixelMap )
    {
        this.spatialPixelMap = spatialPixelMap;
    }

    public void setValue( byte value )
    {
        this.value = value;
    }

    /**
     * Adds edge to global edge table
     */
    public void addEdge( double x1, double x2, double y1, double y2 )
    {
        double minY, maxY, xAtminY, slopeInverse;
        double deltaX = x2 - x1;
        double deltaY = y2 - y1;

        int comparisionY = Double.compare(y1, y2);
        if (comparisionY != 0) // skip horizontal lines
        {
            if (comparisionY < 0)
            {
                minY = y1;
                xAtminY = x1;
                maxY = y2;
                slopeInverse = deltaX / deltaY;
            } else
            {
                minY = y2;
                xAtminY = x2;
                maxY = y1;
                slopeInverse = deltaX / deltaY;
            }
            ScanlineEdge newEdge = new ScanlineEdge(minY, maxY, xAtminY, slopeInverse);
            allEdgesTable.add(newEdge);
            if (Double.compare(minY, globalYmin) < 0)
            {
                globalYmin = minY;
            }
        }
    }

    /**
     * sorts all edges table first by minY and second by X value
     */
    public void finalizeAllEdgesTable()
    {
        Collections.sort(allEdgesTable, new Comparator<ScanlineEdge>()
        {
            @Override
            public int compare( ScanlineEdge scanlineEdge1, ScanlineEdge scanlineEdge2 )
            {
                int comparison = Double.compare(scanlineEdge1.minY, scanlineEdge2.minY);
                if (comparison != 0)
                {
                    return comparison;
                } else
                {
                    return Double.compare(scanlineEdge1.x0, scanlineEdge2.x0);
                }
            }
        });
        allEdgesTableCompleted = true;
    }

    /**
     * performs the scan-line algorithm on finalized (sorted) allEdgesTable
     */
    public void doScanlineFill()
    {
        ScanlineEdge nextEdge;
        if (!allEdgesTableCompleted)
        {
            throw new IllegalStateException("Start of Line Fill only after allEdgesTable is finalized");
        }
        double scanlineY = spatialPixelMap.discretizeY(globalYmin);
        nextEdge = allEdgesTable.pollFirst();
        while (nextEdge != null || !activeEdges.isEmpty())
        {
            //collect active edges
            if (nextEdge != null && Double.compare(nextEdge.minY, scanlineY) <= 0)
            {
                nextEdge.updateX2currentY(scanlineY);
                activeEdges.add(nextEdge);
                nextEdge = allEdgesTable.pollFirst();
            }
            // scan line
            else
            {
                singleLineFill(activeEdges, scanlineY);
                scanlineY += spatialPixelMap.getYStep();
                // update remaining edges
                Iterator<ScanlineEdge> edgeIter = activeEdges.iterator();
                while (edgeIter.hasNext())
                {
                    ScanlineEdge edge = edgeIter.next();
                    if (Double.compare(edge.maxY, scanlineY) <= 0) //edge not active
                    {
                        edgeIter.remove();
                    } else
                    {
                        // adjust x values
                        edge.updateX2currentY(scanlineY);
                    }
                }
            }
        }
    }

    private void singleLineFill( SortedSet<ScanlineEdge> edges, double currentY )
    {
        boolean draw = false;
        double prevY = -Double.MAX_VALUE, prevX = -Double.MAX_VALUE;

        for (ScanlineEdge edge : edges)
        {
            if (edge.xAtscanlineY > prevX)
            {
                if (draw)
                {
                    spatialPixelMap.fillLine(currentY, prevX, edge.xAtscanlineY, value);
                }
                draw = !draw;
                prevX = edge.xAtscanlineY;
            } else //both edges are at same X
            {
                if (((prevY - currentY) * (edge.maxY - currentY)) > 0)
                {
                    draw = !draw;
                }
            }

        }
    }

    // stores relevant data of a polygon edge for scan line algorithm
    private class ScanlineEdge
    {
        private double minY, maxY, x0, xAtscanlineY, slopeInverse;

        private ScanlineEdge( double minY, double maxY, double xAtminY, double slopeInverse )
        {
            this.minY = minY;
            this.maxY = maxY;
            this.x0 = xAtminY;
            this.slopeInverse = slopeInverse;
        }

        private void updateX2currentY( double currentY )
        {
            double deltaY = currentY - minY;
            xAtscanlineY = x0 + slopeInverse * deltaY;
        }
    }

}
