import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.GaussianBlur3D;
import ij.plugin.ImageCalculator;
import ij.plugin.Thresholder;
import ij.process.ImageStatistics;

public class FLSMapper{
	
	private static ImagePlus segment(ImagePlus imp,double sigma,double k,String threshold){
		try{
			int Z = imp.getNSlices();
			int T = imp.getNFrames();
			
			//DoG	
			ImagePlus mask = new Duplicator().run(imp, 1, 1, 1, Z, 1, T);
			ImagePlus sub = new Duplicator().run(mask, 1, 1, 1, Z, 1, T);
			GaussianBlur3D.blur(mask,sigma,sigma,0d);	//Use 3D Gaussian to apply a 2D Gaussian blur. Threading is broken in 2D Gaussian class.
			GaussianBlur3D.blur(sub,sigma*k,sigma*k,0d);
			ImageCalculator ic = new ImageCalculator();
			ic.run("Subtract stack", mask, sub);
			sub.close();
		//if(true){mask.show();return null;}	
		
			//binarise and segment
			IJ.run(mask, "Options...", "iterations=1 count=1 black edm=Overwrite do=Nothing");
			Prefs.blackBackground = true;
			IJ.setAutoThreshold(mask, threshold+" dark stack");
			
			//IJ.run(mask, "Convert to Mask", "method="+threshold+" background=Dark black");	//throws Exception about 8-bit stack
			
			//access private method in Thresholder using reflection
			Method[] methods = Thresholder.class.getDeclaredMethods();
			for(Method meth : methods){
				if(meth.getName()=="applyThreshold"){
					meth.setAccessible(true);
					try{
						meth.invoke(new Thresholder(), mask);		//arguments changed in v1.49k, try both possibilities
					}catch(Exception e){
						meth.invoke(new Thresholder(), mask, false);
					}
					break;
				}
			}
			
			
			IJ.run(mask, "Watershed", "stack");
			IJ.run(mask, "Open", "stack");
			
		//	mask.show();
			
			return mask;
		}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}	
		return null;
	}
	
	public static ArrayList<FLS>[] map(ImagePlus imp,double sigma,double k,String threshold,int base,double minLength,double maxDist){
		int W = imp.getWidth();
		int H = imp.getHeight();
		int Z = imp.getNSlices();
		int T = imp.getNFrames();
		Calibration cal = imp.getCalibration();
		double pixelW = cal.pixelWidth;
		double pixelD = cal.pixelDepth;
		//double frameInterval = cal.frameInterval;
		
		ImagePlus mask = segment(imp,sigma,k,threshold);
		
		@SuppressWarnings("unchecked")
		ArrayList<FLS>[] flsArr = (ArrayList<FLS>[])new ArrayList[T];
		int nextIndex = 1;	//1 based indexing, 0 is unassigned
	try{
		timeloop:
		for(int t=0;t<T;t++){
			flsArr[t] = new ArrayList<FLS>();
			//get base slice rois
			mask.setPosition(1,base,t+1);
			IJ.run(mask, "Create Selection", "");
			Roi baseRoi = mask.getRoi();
			if(baseRoi==null){
				continue timeloop;
			}
			else if(mask.getStatistics().area<(W*H)&&mask.getStatistics().mean==0){
				IJ.run(mask, "Make Inverse", "");
			}
			Roi[] areas = new ShapeRoi(baseRoi).getRois();
			IJ.run(mask, "Select None", "");
			
			//get points through Z
			ArrayList<Point3b> points = new ArrayList<Point3b>();
			ArrayList<Roi> rois = new ArrayList<Roi>();
			for(int z=base+1;z<=Z;z++){
				mask.setPosition(1,z,t+1);
				IJ.run(mask, "Create Selection", "");
				if(mask.getRoi()==null){continue;}
				Roi[] split = new ShapeRoi(mask.getRoi()).getRois();
				if(split.length==0){continue;}
				IJ.run(mask, "Select None", "");
				for(int i=0;i<split.length;i++){
					Rectangle rect = split[i].getBounds();
					points.add(new Point3b((rect.x + (rect.width/2))*pixelW,(rect.y + (rect.height/2))*pixelW,z*pixelD));
					split[i].setPosition(z);
					rois.add(split[i]);
				}
				IJ.run(mask, "Select None", "");
			}
			
			//mask.show();
			mask.close();
			
			//construct FLSs
			imp.setSlice(base);
			for(int b=0;b<areas.length;b++){
			        imp.setPosition(1, base, t+1);
				imp.setRoi(areas[b]);
				IJ.run(imp, "Interpolate", "interval=1 smooth");
				areas[b] = imp.getRoi();
				ImageStatistics stats = imp.getStatistics();
				FLS fls = new FLS(areas[b],stats,pixelW,pixelD,base,minLength);
				Point3b last = fls.coord;
				double minD = Double.POSITIVE_INFINITY;
				int minI = -1;
				boolean end = false;
				while(!end){
					end = true;
					for(int p=0;p<points.size();p++){
						if(points.get(p).z<=last.z){continue;}
						double dist = last.distance(points.get(p));
						if(dist>0.01&&dist<=maxDist&&dist<minD){
							minD = dist;
							minI = p;
						}
					}
					if(minI!=-1){
						fls.addPart(points.get(minI),rois.get(minI));
						last = points.get(minI);
						points.remove(minI);
						rois.remove(minI);
						minD = Double.POSITIVE_INFINITY;
						minI = -1;
						end = false;
					}
				}
				// Accept zero-length FLSs, comment out condition.
				//if(fls.parts.size()!=0){
				fls.index = nextIndex;
				nextIndex++;
				flsArr[t].add(fls);
				//}
			}
			IJ.run(imp, "Select None", "");
			
		}

		//linear assignment of FLS indices
		for(int t=0;t<T-1;t++){
			for(int f0=0;f0<flsArr[t].size();f0++){
				double minD = Double.POSITIVE_INFINITY;
				int minI = -1;
				for(int f1=0;f1<flsArr[t+1].size();f1++){
					double dist = flsArr[t].get(f0).distance(flsArr[t+1].get(f1));
					if(dist<=minD){
						minD = dist;
						minI = f1;
					}
				}
				if(minI!=-1&&minD<=3d){
					flsArr[t+1].get(minI).index = flsArr[t].get(f0).index;
				}
			}
		}
		
		/*  ////circularity histogram values
		int bins = 20;
		int[] circH = new int[bins];
		int max = 0;
		for(int i=0;i<bins;i++){
			circH[i] = 0;
		}
		for(int t=0;t<T;t++){
			for(int f=0;f<flsArr[t].size();f++){
				double circ = flsArr[t].get(f).circularity();
				int ind = (int)Math.floor((circ*bins));
				circH[ind-1]++;
				max = (int)Math.max(max,circH[ind-1]);
			}
		}
		
		////circularity histogram
		//IJ.log(Arrays.toString(circH).replaceAll("[\\[\\]]",""));
		int hw = 500;
		int hh = 500;
		int bar = hw/bins;
		int border = 50;
		int[] normH = new int[bins];
		for(int i=0;i<bins;i++){
			double d = (((double)circH[i])/max)*hh;
			normH[i] = (int)Math.round(d);	
		}
		Image hist = new BufferedImage(hw+(border*2),hh+(border*2),BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = (Graphics2D)hist.getGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0,0,hw+(border*2),hh+(border*2));
		for(int i=0;i<bins;i++){
			g2d.setColor(Color.PINK);
			g2d.fillRect(border+(i*bar),border+hh-normH[i],bar,normH[i]);
			g2d.setColor(Color.WHITE);
		}
		g2d.setColor(Color.WHITE);
		g2d.drawLine(border,border+hh,border+hw,border+hh);	//x-axis
		g2d.drawString("Circularity",border+(hw/2)-30,border+hh+40);
		g2d.drawString("0",border,border+hh+20);	//x labels
		g2d.drawString("1",hw+border,border+hh+20);
		g2d.drawLine(border,border+hh,border,border);	//y-axis
		g2d.drawString("n",10,border+(hh/2));
		for(double y=0d;y<=1d;y+=0.1d){
			g2d.drawString(IJ.d2s((1d-y)*max,0),20,border+Math.round(y*hh)+5);	//y labels
		}
		ImagePlus histogram = new ImagePlus("Circularity Histogram : "+imp.getTitle(),hist);
		histogram.show();
		////  */
		
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return flsArr;
	}
	
}
