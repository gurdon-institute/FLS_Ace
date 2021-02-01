package uk.ac.cam.gurdon;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Collections;
import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.RoiScaler;
import ij.gui.TextRoi;
import ij.measure.ResultsTable;
import ij.process.ImageStatistics;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class FLSOutput {
	private ImagePlus imp;
	private String name;
	private double tCal;
	private ArrayList<FLS>[] flss;
	private ArrayList<ExtraImage> extra;
	private int baseZ;
	private double pixW, pixD;
	private static final double CONTAINED_FRACTION = 0.01d;
	private static final Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
	private static final String foo = System.getProperty("file.separator");

	// constructor for use in high-throughput analysis
	public FLSOutput(ImagePlus imp, String name, double tCal, ArrayList<FLS>[] flss, ArrayList<ExtraImage> extra,
			int baseZ, double pw) {
		this.imp = imp;
		this.name = name;
		this.tCal = tCal;
		this.flss = flss;
		this.baseZ = baseZ;
		this.extra = extra;
		this.pixW = pw;
	}

	// constructor for single stack analysis
	public FLSOutput(ImagePlus imp, double tCal, ArrayList<FLS>[] flss, int baseZ) {
		try {
			this.imp = imp;
			this.name = imp.getTitle();
			this.tCal = tCal;
			this.flss = flss;
			this.baseZ = baseZ;
			this.pixW = imp.getCalibration().pixelWidth;
			this.pixD = imp.getCalibration().pixelDepth;
			overlay(imp);
			table(true);
			trace(true);
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	public ResultsTable trace(boolean show_results_window) {
		try {
			ImagePlus fascin = null;
			if (extra != null) {
				for (ExtraImage e : extra) {
					if (e.image == null) {
						continue;
					}
					if (e.name.matches(".*[Ff]ascin.*") && e.image.getNSlices() > 1) {
						fascin = e.image;
						break;
					}
				}
			}
			ResultsTable traceTable = new ResultsTable();
			traceTable.showRowNumbers(false);
			int row = 0;
			for (int t = 0; t < flss.length; t++) {
				for (int f = 0; f < flss[t].size(); f++) {
					FLS fls = flss[t].get(f);
					traceTable.setValue("Index", row, fls.index);
					traceTable.setValue("Time", row, t);
					double csum = 0d;
					for (int i = 0; i < fls.rois.size(); i++) {
						imp.setPosition(fls.rois.get(i).getPosition());
						imp.setRoi(fls.rois.get(i));
						ImageStatistics stats = imp.getStatistics();
						traceTable.setValue("Actin Mean base+" + i, row, stats.mean);
						traceTable.setValue("Actin Area base+" + i, row, stats.area);
						traceTable.setValue("Actin Integrated Density base+" + i, row, stats.mean * stats.area);
						csum += stats.mean * stats.pixelCount;
						traceTable.setValue("Actin Cumulative Sum base+" + i, row, csum);
						if (fascin != null) {
							fascin.setPosition(fls.rois.get(i).getPosition());
							fascin.setRoi(fls.rois.get(i));
							traceTable.setValue("Fascin Mean base+" + i, row, fascin.getStatistics().mean);
						}
					}
					row++;
				}

			}
			if (show_results_window)
				traceTable.show(name + " Traces");
			return traceTable;
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
		return new ResultsTable();
	}

	public void overlay(ImagePlus imp) {
		try {
			int Z = imp.getNSlices();
			int T = imp.getNFrames();

			Color colour = Color.RED;
			Overlay ol = new Overlay();
			for (int t = 0; t < flss.length; t++) {
				for (int f = 0; f < flss[t].size(); f++) {
					if (flss[t].get(f).isReal() == 1) {
						colour = Color.GREEN;
					} else {
						colour = Color.RED;
					}
					int x = (int) Math.round(flss[t].get(f).coord.x / pixW);
					int y = (int) Math.round(flss[t].get(f).coord.y / pixW);
					Roi base = flss[t].get(f).base;
					if (Z > 1 && T > 1) {
						base.setPosition(1, baseZ, t + 1);
					} else if (Z > 1 && T == 1) {
						base.setPosition(baseZ);
					}
					base.setStrokeColor(colour);
					ol.add(base);
					Roi localbg = flss[t].get(f).localbg;
					if (localbg != null) {
						if (Z > 1 && T > 1) {
							localbg.setPosition(1, baseZ, t + 1);
						} else if (Z > 1 && T == 1) {
							localbg.setPosition(baseZ);
						}
						localbg.setStrokeColor(Color.YELLOW);
						ol.add(localbg);
					}
					TextRoi label = new TextRoi(x, y, "" + flss[t].get(f).index, labelFont);
					if (Z > 1 && T > 1) {
						label.setPosition(1, baseZ, t + 1);
					} else if (Z > 1 && T == 1) {
						label.setPosition(baseZ);
					}
					label.setStrokeColor(Color.CYAN);
					ol.add(label);
					for (int p = 0; p < flss[t].get(f).parts.size(); p++) {
						Roi part = new Roi((flss[t].get(f).parts.get(p).x / pixW) - 1,
								(flss[t].get(f).parts.get(p).y / pixW) - 1, 3, 3);
						TextRoi partLabel = new TextRoi((flss[t].get(f).parts.get(p).x / pixW) - 1,
								(flss[t].get(f).parts.get(p).y / pixW), "" + flss[t].get(f).index, labelFont);
						int z = (int) Math.round(flss[t].get(f).parts.get(p).z / pixD);
						if (Z > 1 && T > 1) {
							part.setPosition(1, z, t + 1);
							partLabel.setPosition(1, z, t + 1);
						} else if (Z > 1 && T == 1) {
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
			// imp.show();
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	public String json() {
		HashMap<Integer, HashMap<Integer, FLS>> fls_time = new HashMap<Integer, HashMap<Integer, FLS>>();
		HashMap<Integer, HashMap<String, ImagePlus>> protein_imps = new HashMap<Integer, HashMap<String, ImagePlus>>();
		for (int t = 0; t < flss.length; t++) {
			for (int i = 0; i < flss[t].size(); i++) {
				FLS fls = flss[t].get(i);
				if (fls_time.containsKey(fls.index)) {
					fls_time.get(fls.index).put(t, fls);
				} else {
					HashMap<Integer, FLS> entry = new HashMap<Integer, FLS>();
					entry.put(t, fls);
					fls_time.put(fls.index, entry);
				}
			}

			// Preload extra images
			HashMap<String, ImagePlus> tp_imps = new HashMap<String, ImagePlus>();
			protein_imps.put(t, tp_imps);
			if (flss[t].size() > 0) {
				String ePath = flss[t].get(0).expPath + foo + "t" + (t + 1) + foo;
				File[] eFiles = new File(ePath).listFiles();
				for (ExtraImage e : extra) {
					for (int k = 0; k < eFiles.length; k++) {
						if (eFiles[k].getAbsolutePath().matches(".*" + e.regex + ".TIF")) {
							tp_imps.put(e.name, IJ.openImage(eFiles[k].getAbsolutePath()));
							break;
						}
					}
				}
			}
		}

		JSONArray js_fls_array = new JSONArray();
		HashMap<Integer, ShapeRoi> allflsrois = new HashMap<Integer, ShapeRoi>();
		RoiScaler rs = new RoiScaler();
		for (HashMap.Entry<Integer, HashMap<Integer, FLS>> entry : fls_time.entrySet()) {
			JSONObject js_fls_entry = new JSONObject();
			JSONObject js_fls_timepoints = new JSONObject();
			HashMap<Integer, FLS> timepoints = entry.getValue();
			File expPath = null;
			for (HashMap.Entry<Integer, FLS> fls_entry : timepoints.entrySet()) {
				int t = fls_entry.getKey();
				HashMap<String, ImagePlus> tp_imps = protein_imps.get(t);
				FLS fls = fls_entry.getValue();

				JSONObject js_timepoint_entry = fls.to_json();
				JSONArray js_shaft_actin_mean = new JSONArray();
				JSONArray js_shaft_actin_area = new JSONArray();
				JSONArray js_shaft_actin_bgmean = new JSONArray();
				JSONArray js_shaft_actin_bgstd = new JSONArray();

				for (int i = 0; i < fls.rois.size(); i++) {
					Roi roi = fls.rois.get(i);
					imp.setPosition(1, baseZ + i + 1, t + 1);
					// imp.setPosition(roi.getPosition());
					imp.setRoi(roi);
					ImageStatistics stats = imp.getStatistics();
					js_shaft_actin_mean.add(stats.mean);
					js_shaft_actin_area.add(stats.area);

					ShapeRoi localbg = new ShapeRoi(rs.scale(roi, 3, 3, true));
					localbg = localbg.not(new ShapeRoi(rs.scale(roi, 2, 2, true)));
					imp.setRoi(localbg);
					stats = imp.getStatistics();
					js_shaft_actin_bgmean.add(stats.mean);
					js_shaft_actin_bgstd.add(stats.stdDev);

				}

				js_timepoint_entry.put("shaftActinMean", js_shaft_actin_mean);
				js_timepoint_entry.put("shaftActinArea", js_shaft_actin_area);
				js_timepoint_entry.put("shaftActinBackgroundMean", js_shaft_actin_bgmean);
				js_timepoint_entry.put("shaftActinBackgroundStd", js_shaft_actin_bgstd);

				for (HashMap.Entry<String, ImagePlus> tp_imp_entry : tp_imps.entrySet()) {
					ImagePlus eimp = tp_imp_entry.getValue();
					if (eimp.getNSlices() < 2)
						continue;
					String ename = tp_imp_entry.getKey();

					JSONArray js_shaft_extra_mean = new JSONArray();
					JSONArray js_shaft_extra_bgmean = new JSONArray();
					JSONArray js_shaft_extra_bgstd = new JSONArray();
					for (int i = 0; i < fls.rois.size(); i++) {
						Roi roi = fls.rois.get(i);
						eimp.setPosition(1, baseZ + i + 1, t + 1);
						eimp.setRoi(roi);
						ImageStatistics stats = eimp.getStatistics();
						js_shaft_extra_mean.add(stats.mean);

						ShapeRoi localbg = new ShapeRoi(rs.scale(roi, 3, 3, true));
						localbg = localbg.not(new ShapeRoi(rs.scale(roi, 2, 2, true)));
						eimp.setRoi(localbg);
						stats = eimp.getStatistics();
						js_shaft_extra_bgmean.add(stats.mean);
						js_shaft_extra_bgstd.add(stats.stdDev);

					}
					js_timepoint_entry.put("shaft" + ename + "Mean", js_shaft_extra_mean);
					js_timepoint_entry.put("shaft" + ename + "BackgroundMean", js_shaft_extra_bgmean);
					js_timepoint_entry.put("shaft" + ename + "BackgroundStd", js_shaft_extra_bgstd);
				}

				js_timepoint_entry.put("Time (sec)", fls_entry.getKey() * tCal);
				js_fls_timepoints.put(fls_entry.getKey(), js_timepoint_entry);
				expPath = fls_entry.getValue().expPath;
			}
			js_fls_entry.put("experiment", expPath.getAbsolutePath());
			js_fls_entry.put("timepoints", js_fls_timepoints);

			int first_tp = Collections.min(timepoints.keySet());
			if (first_tp > 0) {
				FLS first_fls = timepoints.get(first_tp);
				JSONObject pre_intensities_tps = new JSONObject();
				JSONObject pre_bgintensities_tps = new JSONObject();
				JSONObject pre_bgstdintensities_tps = new JSONObject();
				for (int t = 0; t < first_tp; t++) {
					HashMap<String, ImagePlus> tp_imps = protein_imps.get(t);

					if (!allflsrois.containsKey(t)) {
						ShapeRoi allflsroi = new ShapeRoi(new Roi(0, 0, 0, 0));
						for (FLS fls : flss[t]) {
							allflsroi = allflsroi.and(new ShapeRoi(rs.scale(fls.base, 2, 2, true)));
						}
						allflsrois.put(t, allflsroi);
					}
					ShapeRoi allflsroi = allflsrois.get(t);

					JSONObject pre_intensities = new JSONObject();
					JSONObject pre_bgintensities = new JSONObject();
					JSONObject pre_bgstdintensities = new JSONObject();

					for (HashMap.Entry<String, ImagePlus> tp_imp_entry : protein_imps.get(t).entrySet()) {
						ImagePlus eimp = tp_imp_entry.getValue();
						String imgname = tp_imp_entry.getKey();
						eimp.setRoi(first_fls.base);
						ImageStatistics stats = eimp.getStatistics();
						pre_intensities.put(imgname, stats.mean);

						ShapeRoi localbg = new ShapeRoi(rs.scale(first_fls.base, 3, 3, true));
						localbg = localbg.not(allflsroi);
						eimp.setRoi(localbg);
						stats = eimp.getStatistics();
						pre_bgintensities.put(imgname, stats.mean);
						pre_bgstdintensities.put(imgname, stats.stdDev);
					}
					pre_intensities_tps.put(t, pre_intensities);
					pre_bgintensities_tps.put(t, pre_bgintensities);
					pre_bgstdintensities_tps.put(t, pre_bgstdintensities);
				}
				js_fls_entry.put("preIntensitiesMean", pre_intensities_tps);
				js_fls_entry.put("preBackgroundIntensitiesMean", pre_bgintensities_tps);
				js_fls_entry.put("preBackgroundIntensitiesStd", pre_bgstdintensities_tps);
			}

			js_fls_array.add(js_fls_entry);
		}

		for (HashMap<String, ImagePlus> tpentry : protein_imps.values()) {
			for (ImagePlus imp : tpentry.values())
				imp.close();
		}

		return js_fls_array.toJSONString();
	}

	public ResultsTable table(boolean show_results_window) {
		try {
			ResultsTable rt = new ResultsTable();
			rt.setPrecision(4);
			rt.showRowNumbers(false);
			int row = 0;
			for (int t = 0; t < flss.length; t++) {
				for (int f = 0; f < flss[t].size(); f++) {
					FLS fls = flss[t].get(f);
					rt.setValue("Timepoint", row, t);
					rt.setValue("Time (sec)", row, t * tCal);
					rt.setValue("Index", row, fls.index);
					rt.setValue("Straight (\u00B5m)", row, fls.straightLength());
					rt.setValue("Path (\u00B5m)", row, flss[t].get(f).pathLength());
					rt.setValue("Straightness", row, fls.straightness());
					rt.setValue("Base Area (\u00B5m\u00B2)", row, fls.baseArea);
					rt.setValue("Base Circularity", row, fls.circularity());
					rt.setValue("X", row, fls.coord.x);
					rt.setValue("Y", row, fls.coord.y);
					rt.setValue("Is an FLS?", row, fls.isReal());
					rt.setValue("Actin Mean", row, fls.actinMean);
					rt.setValue("Actin Background Mean", row, fls.actinBackgroundMean);
					rt.setValue("Actin Background Std", row, fls.actinBackgroundStd);
					rt.setValue("Local Actin Background Mean", row, fls.localActinBackgroundMean);
					rt.setValue("Local Actin Background Std", row, fls.localActinBackgroundStd);
					if (fls.TIRFstats != null) {
						rt.setValue("Actin TIRF Object Mean", row, fls.TIRFstats.mean);
						// double area = fls.TIRFstats.area;
						rt.setValue("Actin TIRF Object Area (\u00B5m\u00B2)", row, fls.TIRFstats.area * pixW * pixW);
						double perim = fls.tirfRoi.getLength();
						double circ = (4d * Math.PI * fls.TIRFstats.area) / (perim * perim);
						if (circ < 0.05) { // fix for calibration bug
							perim = perim * pixW;
							circ = (4d * Math.PI * fls.TIRFstats.area) / (perim * perim);
						}
						if (circ > 1) {
							circ = 1;
						}
						rt.setValue("Actin TIRF Object Circularity", row, circ);
					} else if (extra != null) { // make all columns for every output, pointless but requested by
												// Geoorogrow
						rt.setValue("Actin TIRF Object Mean", row, -1);
						rt.setValue("Actin TIRF Object Area (\u00B5m\u00B2)", row, -1);
						rt.setValue("Actin TIRF Object Circularity", row, -1);
					}
					String[] keys = fls.geneMeans.keySet().toArray(new String[0]);
					if (extra != null) {
						for (ExtraImage e : extra) {
							boolean got = false;
							for (int g = 0; g < keys.length; g++) {
								// IJ.log(keys[g]);
								if (keys[g].matches(e.regex) || keys[g].matches(e.name)) {
									// IJ.log(keys[g]+" matches "+e.regex+" for "+e.name);
									double mean = fls.geneMeans.get(keys[g]);
									rt.setValue(e.name, row, mean);
									got = true;
								}
								// else{IJ.log(keys[g]+" !matches "+e.regex+" for "+e.name);}
							}
							if (!got) {
								rt.setValue(e.name, row, -1);
							}
						}
						for (ExtraImage e : extra) {
							boolean got = false;
							for (int g = 0; g < keys.length; g++) {
								// IJ.log(keys[g]);
								if (keys[g].matches(e.regex) || keys[g].matches(e.name)) {
									// IJ.log(keys[g]+" matches "+e.regex+" for "+e.name);
									double background_mean = fls.geneBackgroundMeans.get(keys[g]);
									double background_std = fls.geneBackgroundStds.get(keys[g]);
									rt.setValue(e.name + "_background", row, background_mean);
									rt.setValue(e.name + "_std", row, background_std);
									got = true;
								}
								// else{IJ.log(keys[g]+" !matches "+e.regex+" for "+e.name);}
							}
							if (!got) {
								rt.setValue(e.name + "_background", row, -1);
							}
						}
						for (ExtraImage e : extra) {
							boolean got = false;
							for (int g = 0; g < keys.length; g++) {
								if (keys[g].matches(e.regex)) {
									double frac = fls.geneFractions.get(keys[g]);
									int has = (frac >= CONTAINED_FRACTION) ? 1 : 0;
									// rt.setValue(e.name+" Fraction",row,frac); //leave in for testing
									// if(e.name.matches("actin-TIRF")&&fls.geneMeans.get(keys[g])>0){
									// has = 1;
									// }
									rt.setValue("Contains " + e.name + "?", row, has);
									got = true;
								}
							}
							if (!got) {
								rt.setValue("Contains " + e.name + "?", row, -1);
							}
						}
					}
					row++;
				}
			}
			if (show_results_window)
				rt.show("FLS_Ace " + name);
			return rt;
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
		return new ResultsTable();
	}

}
