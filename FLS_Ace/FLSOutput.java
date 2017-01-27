import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class FLSOutput{
private ImagePlus imp;
private String name;
private double tCal;
private ArrayList<FLS>[] flss;
private ArrayList<ExtraImage> extra;
private int baseZ;
private double pixW, pixD;
private static final double CONTAINED_FRACTION = 0.01d;
private static final Font labelFont = new Font(Font.SANS_SERIF,Font.PLAIN,10);

	//constructor for use in high-throughput analysis
	public FLSOutput(ImagePlus imp,String name,double tCal,ArrayList<FLS>[] flss,ArrayList<ExtraImage> extra,int baseZ,double pw){
		this.imp = imp;
		this.name = name;
		this.tCal = tCal;
		this.flss = flss;
		this.baseZ = baseZ;
		this.extra = extra;
		this.pixW = pw;
	}

	//constructor for single stack analysis
	public FLSOutput(ImagePlus imp,double tCal,ArrayList<FLS>[] flss,int baseZ){
	try{
		this.imp = imp;
		this.name = imp.getTitle();
		this.tCal = tCal;
		this.flss = flss;
		this.baseZ = baseZ;
		this.pixW = imp.getCalibration().pixelWidth;
		this.pixD = imp.getCalibration().pixelDepth;
		overlay(imp);
		table();
		trace();
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}	
	}
	
	public void trace(){
	try{
		ImagePlus fascin = null;
		if(extra!=null){
			for(ExtraImage e:extra){
				if(e.image==null){continue;}
				if(e.name.matches(".*[Ff]ascin.*")&&e.image.getNSlices()>1){
					fascin = e.image;
					break;
				}
			}
		}
		ResultsTable traceTable = new ResultsTable();
		traceTable.showRowNumbers(false);
		int row = 0;
		for(int t=0;t<flss.length;t++){
			for(int f=0;f<flss[t].size();f++){
				FLS fls = flss[t].get(f);
				traceTable.setValue("Index",row,fls.index);
				traceTable.setValue("Time",row,t);
				double csum = 0d;
				for(int i=0;i<fls.rois.size();i++){
					imp.setPosition(fls.rois.get(i).getPosition());
					imp.setRoi(fls.rois.get(i));
					ImageStatistics stats = imp.getStatistics();
					traceTable.setValue("Actin Mean base+"+i,row,stats.mean);
					traceTable.setValue("Actin Area base+"+i,row,stats.area);
					traceTable.setValue("Actin Integrated Density base+"+i,row,stats.mean*stats.area);
					csum += stats.mean*stats.pixelCount;
					traceTable.setValue("Actin Cumulative Sum base+"+i,row,csum);
					if(fascin!=null){
						fascin.setPosition(fls.rois.get(i).getPosition());
						fascin.setRoi(fls.rois.get(i));
						traceTable.setValue("Fascin Mean base+"+i,row,fascin.getStatistics().mean);
					}
				}
				row++;
			}
			
		}
		traceTable.show(name+" Traces");
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}	
	}
	
	public void overlay(ImagePlus imp){
	try{
		int Z = imp.getNSlices();
		int T = imp.getNFrames();
		
		Color colour = Color.RED;
		Overlay ol = new Overlay();
		for(int t=0;t<flss.length;t++){
			for(int f=0;f<flss[t].size();f++){
				if(flss[t].get(f).isReal()==1){colour = Color.GREEN;}
				else{colour = Color.RED;}
				int x = (int)Math.round(flss[t].get(f).coord.x/pixW);
				int y = (int)Math.round(flss[t].get(f).coord.y/pixW);
				Roi base = flss[t].get(f).base;
				if(Z>1&&T>1){
					base.setPosition(1,baseZ,t+1);
				}
				else if(Z>1&&T==1){
					base.setPosition(baseZ);
				}
				base.setStrokeColor(colour);
				ol.add(base);
				TextRoi label = new TextRoi(x,y,""+flss[t].get(f).index,labelFont);
				if(Z>1&&T>1){
					label.setPosition(1,baseZ,t+1);
				}
				else if(Z>1&&T==1){
					label.setPosition(baseZ);
				}
				label.setStrokeColor(Color.CYAN);
				ol.add(label);
				for(int p=0;p<flss[t].get(f).parts.size();p++){
					Roi part = new Roi((flss[t].get(f).parts.get(p).x/pixW)-1,(flss[t].get(f).parts.get(p).y/pixW)-1,3,3);
					TextRoi partLabel = new TextRoi((flss[t].get(f).parts.get(p).x/pixW)-1,(flss[t].get(f).parts.get(p).y/pixW),""+flss[t].get(f).index,labelFont);
					int z = (int)Math.round(flss[t].get(f).parts.get(p).z/pixD);
					if(Z>1&&T>1){
						part.setPosition(1,z,t+1);
						partLabel.setPosition(1,z,t+1);
					}
					else if(Z>1&&T==1){
						part.setPosition(z);
						partLabel.setPosition(z);
					}
					part.setStrokeColor(colour);
					part.setCornerDiameter(2);
					partLabel.setStrokeColor(Color.CYAN);
					ol.add(part);
					ol.add(partLabel);
				}
			}
		}
		IJ.run("Overlay Options...", "set");
		imp.setOverlay(ol);
		//imp.show();
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public void table(){
	try{
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(4);
		rt.showRowNumbers(false);
		int row = 0;
		for(int t=0;t<flss.length;t++){
			for(int f=0;f<flss[t].size();f++){
				FLS fls = flss[t].get(f);
				rt.setValue("Timepoint",row,t);
				rt.setValue("Time (sec)",row,t*tCal);
				rt.setValue("Index",row,fls.index);
				rt.setValue("Straight (\u00B5m)",row,fls.straightLength());
				rt.setValue("Path (\u00B5m)",row,flss[t].get(f).pathLength());
				rt.setValue("Straightness",row,fls.straightness());
				rt.setValue("Base Area (\u00B5m\u00B2)",row,fls.baseArea);
				rt.setValue("Base Circularity",row,fls.circularity());
				rt.setValue("X",row,fls.coord.x);
				rt.setValue("Y",row,fls.coord.y);
				rt.setValue("Is an FLS?",row,fls.isReal());
				rt.setValue("Actin Mean",row,fls.actinMean);
				if(fls.TIRFstats!=null){
					rt.setValue("Actin TIRF Object Mean",row,fls.TIRFstats.mean);
					double area = fls.TIRFstats.area;
					rt.setValue("Actin TIRF Object Area (\u00B5m\u00B2)",row,fls.TIRFstats.area*pixW*pixW);
					double perim = fls.tirfRoi.getLength();
					double circ = (4d*Math.PI*fls.TIRFstats.area)/(perim*perim);
					if(circ<0.05){	//fix for calibration bug
						perim = perim*pixW;
						circ = (4d*Math.PI*fls.TIRFstats.area)/(perim*perim);
					}
					if(circ>1){circ=1;}
					rt.setValue("Actin TIRF Object Circularity",row,circ);
				}
				else if(extra!=null){	//make all columns for every output, pointless but requested by Geoorogrow
					rt.setValue("Actin TIRF Object Mean",row,-1);
					rt.setValue("Actin TIRF Object Area (\u00B5m\u00B2)",row,-1);
					rt.setValue("Actin TIRF Object Circularity",row,-1);
				}
				String[] keys = fls.geneMeans.keySet().toArray(new String[0]);
				if(extra!=null){
					for(ExtraImage e:extra){
						boolean got = false;
						for(int g=0;g<keys.length;g++){ 
							//IJ.log(keys[g]);
							if(keys[g].matches(e.regex)){
								//IJ.log(keys[g]+" matches "+e.regex+" for "+e.name);
								double mean = fls.geneMeans.get(keys[g]);
								rt.setValue(e.name,row,mean);
								got = true;
							}
							//else{IJ.log(keys[g]+" !matches "+e.regex+" for "+e.name);}
						}
						if(!got){
							rt.setValue(e.name,row,-1);
						}
					}
					for(ExtraImage e:extra){
						boolean got = false;
						for(int g=0;g<keys.length;g++){
							if(keys[g].matches(e.regex)){
								double frac = fls.geneFractions.get(keys[g]);
								int has = (frac>=CONTAINED_FRACTION)?1:0;
								//rt.setValue(e.name+" Fraction",row,frac);	//leave in for testing
								//if(e.name.matches("actin-TIRF")&&fls.geneMeans.get(keys[g])>0){
								//	has = 1;
								//}
								rt.setValue("Contains "+e.name+"?",row,has);
								got = true;
							}
						}
						if(!got){
							rt.setValue("Contains "+e.name+"?",row,-1);
						}
					}
				}
				row++;
			}
		}
		if(rt==null){throw new Exception("ResultsTable null for "+name);}
		rt.show("FLS_Ace "+name);
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
}
