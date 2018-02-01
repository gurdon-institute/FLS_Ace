import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.io.File;

import ij.IJ;
import ij.gui.Roi;
import ij.process.ImageStatistics;


public class FLS{
public ArrayList<Point3b> parts;
public ArrayList<Roi> rois;
public Roi base, localbg;
public Point3b coord;
public int index;
public double path, straight, baseArea, perim, circ, pixelW, pixelD;
public double actinMean, actinBackgroundMean, actinBackgroundStd, minLength;
public double localActinBackgroundMean, localActinBackgroundStd;
public HashMap<String, Double> geneMeans;
public HashMap<String, Double> geneFractions;
public HashMap<String, Double> geneBackgroundMeans;
public HashMap<String, Double> geneBackgroundStds;
public Roi tirfRoi;
public ImageStatistics TIRFstats;
public File expPath;
    
        public FLS(Roi base,ImageStatistics stats,double pixelW,double pixelD,int baseZ,double minLength){
	try{
		this.base = base;
		this.actinMean = stats.mean;
		this.actinBackgroundMean = -1d;
		this.actinBackgroundStd = -1d;
		this.localActinBackgroundMean = -1d;
		this.localActinBackgroundStd = -1d;
		Rectangle rect = base.getBounds();
		this.coord = new Point3b((rect.x+(rect.width/2))*pixelW,(rect.y+(rect.height/2))*pixelW,baseZ*pixelD);
		this.parts = new ArrayList<Point3b>();
		this.rois = new ArrayList<Roi>();	//Rois along the length of the FLS
		this.index = -1;
		this.pixelW = pixelW;
		this.pixelD = pixelD;
		this.minLength = minLength;
		path = 1d;
		straight = -1d;
		baseArea = stats.area;
		perim = -1d;
		circ = -1d;
		geneMeans = new HashMap<String,Double>();
		geneFractions = new HashMap<String,Double>();
		geneBackgroundMeans = new HashMap<String,Double>();
		geneBackgroundStds = new HashMap<String,Double>();
		this.expPath = null;
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public boolean contains(int x, int y){
	try{
		if(base!=null&&base.contains(x,y)){return true;}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return false;
	}
	
	public void setTIRFStats(Roi r,ImageStatistics stats){
		try{
		this.tirfRoi = r;
		this.TIRFstats = stats;
		}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public void addPart(Point3b point, Roi roi){
	try{
		parts.add(point);
		rois.add(roi);
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public double distance(FLS other){
		return this.coord.distance(other.coord);
	}
	
	public void addGeneMean(String name,double mean){
		geneMeans.put(name,mean);
	}
	
	public void addGeneFraction(String name,double frac){
		geneFractions.put(name,frac);
	}

        public void addGeneBackground(String name,double background, double std){
		geneBackgroundMeans.put(name,background);
		geneBackgroundStds.put(name,std);
	}

	public double straightLength(){
	        if (parts.size() == 0) {
		        straight = 0d;
		} else {
		        double longest = -1d;
			for(int p=0;p<parts.size();p++){
			        double ed = coord.distance(parts.get(p));
				longest = Math.max(longest,ed);
			}
			straight = longest;
		}
		return straight;
	}
	
	public double pathLength(){
	        if (parts.size() == 0) {
		        path = 0d;
	        } else {
	                path = coord.distance(parts.get(0));	
	                for(int p=1;p<parts.size();p++){
			        path += parts.get(p-1).distance(parts.get(p));
			}
	        }
		return path;
	}
	
	public double straightness(){
	try{
		if(straight<0){straightLength();}
		if(path<0){pathLength();}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return straight/path;
	}
	
	public double circularity(){
	try{
		if(perim<0){
			perim = base.getLength();
		}
		if(circ<0){
			circ = (4d*Math.PI*baseArea)/(perim*perim);
			if(circ<0.05){	//bug fix - base.getLength returns uncalibrated value in high-throughput mode
				perim = perim*pixelW;
				circ = (4d*Math.PI*baseArea)/(perim*perim);
			}
			if(circ>1){circ=1;}
		}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return circ;
	}
	
	public int isReal(){
	try{
		if(straight<0){straightLength();}
		if(circ<0){circularity();}
		if(straight>=minLength&&baseArea<=20d&&circ>=0.5d){return 1;}
		else{return 0;}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return 0;
	}

}
