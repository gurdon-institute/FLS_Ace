package uk.ac.cam.gurdon;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.Concatenator;
import ij.plugin.HyperStackConverter;
import ij.plugin.RoiScaler;
import ij.process.ImageStatistics;

@Plugin(type = Command.class, menuPath = "Plugins>FLS Ace")
public class FLS_Ace implements Command, ActionListener {
	private JFrame gui;
	private JSpinner baseSpinner;
	private JTextField Wfield, Dfield, Tfield, sigmaField, kField, lengthField, distField;
	private JComboBox<String> methodCombo;
	private JCheckBox saveTick;
	private int base = (int) Math.round(Prefs.get("FLS_Ace.base", 3));
	private double voxelW = Prefs.get("FLS_Ace.voxelW", 0.1487d);
	private double voxelD = Prefs.get("FLS_Ace.voxelD", 1d);
	private double tCal = Prefs.get("FLS_Ace.tCal", 1d);
	private double actinSigma = Prefs.get("FLS_Ace.actinSigma", 1.5d);
	private double actinK = Prefs.get("FLS_Ace.actinK", 3d);
	private String actinMethod = Prefs.get("FLS_Ace.actinMethod", "Triangle");
	private String path = Prefs.get("FLS_Ace.path", System.getProperty("user.home"));
	private boolean save = Prefs.get("FLS_Ace.save", false);
	private double minLength = Prefs.get("FLS_Ace.minLength", 3d);
	private double maxDist = Prefs.get("FLS_Ace.maxDist", 2d);
	private static final String[] methods = { "Triangle", "Otsu", "Huang", "MaxEntropy" };
	private static final String foo = System.getProperty("file.separator");
	private static final String expRegex = ".*exp[0-9]{1,2}[a-zA-Z]?";
	private static final Pattern timeRegex = Pattern.compile("t[0-9]+$");

	public void run() {
		try {
			gui = new JFrame("FLS_Ace");
			gui.setLayout(new BoxLayout(gui.getContentPane(), BoxLayout.Y_AXIS));

			JPanel controlPanel = new JPanel();
			baseSpinner = new JSpinner(new SpinnerNumberModel(base, 1, 100, 1));
			controlPanel.add(new JLabel("Base Z:"));
			controlPanel.add(baseSpinner);
			saveTick = new JCheckBox("Save batch images", save);
			controlPanel.add(saveTick);
			gui.add(controlPanel);

			JPanel fieldPanel = new JPanel(new GridLayout(0, 3, 2, 2));
			fieldPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 0));
			Wfield = new JTextField("" + voxelW, 2);
			fieldPanel.add(new JLabel("Voxel Width:"));
			fieldPanel.add(Wfield);
			fieldPanel.add(new JLabel("\u00b5m"));

			Dfield = new JTextField("" + voxelD, 2);
			fieldPanel.add(new JLabel("Voxel Depth:"));
			fieldPanel.add(Dfield);
			fieldPanel.add(new JLabel("\u00b5m"));

			Tfield = new JTextField("" + tCal, 2);
			fieldPanel.add(new JLabel("Frame Interval:"));
			fieldPanel.add(Tfield);
			fieldPanel.add(new JLabel("sec"));

			fieldPanel.add(Box.createGlue());
			fieldPanel.add(Box.createGlue());
			fieldPanel.add(Box.createGlue());

			sigmaField = new JTextField("" + actinSigma, 2);
			fieldPanel.add(new JLabel("Actin Sigma:"));
			fieldPanel.add(sigmaField);
			fieldPanel.add(new JLabel("\u00b5m"));

			kField = new JTextField("" + actinK, 2);
			fieldPanel.add(new JLabel("Actin K:"));
			fieldPanel.add(kField);
			fieldPanel.add(Box.createGlue());

			methodCombo = new JComboBox(methods);
			methodCombo.setSelectedItem(actinMethod);
			fieldPanel.add(new JLabel("Actin Threshold:"));
			fieldPanel.add(methodCombo);
			fieldPanel.add(Box.createGlue());

			lengthField = new JTextField("" + minLength, 2);
			fieldPanel.add(new JLabel("Min FLS Length:"));
			fieldPanel.add(lengthField);
			fieldPanel.add(new JLabel("\u00b5m"));

			distField = new JTextField("" + maxDist, 2);
			fieldPanel.add(new JLabel("Tracing Distance:"));
			fieldPanel.add(distField);
			fieldPanel.add(new JLabel("\u00b5m"));

			gui.add(fieldPanel);

			JPanel buttonPanel = new JPanel();
			JButton currentButton = new JButton("current");
			currentButton.addActionListener(this);
			buttonPanel.add(currentButton);
			JButton batchButton = new JButton("batch");
			batchButton.addActionListener(this);
			buttonPanel.add(batchButton);
			JButton configButton = new JButton("config");
			configButton.addActionListener(this);
			buttonPanel.add(configButton);
			gui.add(buttonPanel);
			gui.pack();
			gui.setLocationRelativeTo(null);
			gui.setVisible(true);
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	private void current() {
		try {
			if (WindowManager.getImageCount() == 0) {
				IJ.error("No images are open.");
				return;
			}
			ArrayList<ExtraImage> extra = getExtraConfig();
			ImagePlus image = WindowManager.getCurrentImage();
			String title = image.getTitle();
			IJ.run(image, "Select None", "");
			boolean got = false;
			for (ExtraImage e : extra) {
				if (title.matches(e.regex + ".*")) {
					ImagePlus mask = e.mask(image);
					IJ.run(mask, "Create Selection", "");
					if (mask.getRoi() != null) {
						Roi maskRoi = mask.getRoi();
						maskRoi.setStrokeColor(Color.MAGENTA);
						Overlay mol = new Overlay();
						mol.add(maskRoi);
						image.setOverlay(mol);
					}
					mask.close();
					got = true;
					break;
				}
			}
			if (!got) {
				ImagePlus actin = WindowManager.getCurrentImage();
				Calibration cal = new Calibration();
				cal.setUnit("\u00b5m");
				cal.setTimeUnit("sec");
				cal.frameInterval = tCal;
				cal.pixelWidth = voxelW;
				cal.pixelHeight = voxelW;
				cal.pixelDepth = voxelD;
				actin.setCalibration(cal);
				actin.setOverlay(null);
				int slice = 1;
				if (image.getNSlices() >= base) {
					slice = base;
				}
				ArrayList<FLS>[] flss = FLSMapper.map(actin, actinSigma, actinK, actinMethod, slice, minLength,
						maxDist);
				new FLSOutput(actin, tCal, flss, base);
			}
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	private ArrayList<ExtraImage> getExtraConfig() {
		ArrayList<ExtraImage> ei = new ArrayList<ExtraImage>();
		try {
			File configFile = new File(System.getProperty("user.home") + foo + "FLS_Ace_config.xml");
			if (!configFile.exists()) {
				createDefaultConfig();
			}
			FileInputStream stream = new FileInputStream(configFile);
			FileChannel fc = stream.getChannel();
			String str = Charset.defaultCharset().decode(fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size()))
					.toString();
			String[] genes = str.split("<gene>"); // there are better ways to parse XML, but this is easy
			for (int g = 0; g < genes.length; g++) {
				if (!genes[g].contains("<name>")) {
					continue;
				}
				String name = genes[g].substring(genes[g].indexOf("<name>") + 6, genes[g].indexOf("</name>"));
				String regex = genes[g].substring(genes[g].indexOf("<regex>") + 7, genes[g].indexOf("</regex>"));
				String method = genes[g].substring(genes[g].indexOf("<method>") + 8, genes[g].indexOf("</method>"));
				String sigma = genes[g].substring(genes[g].indexOf("<sigma>") + 7, genes[g].indexOf("</sigma>"));
				String k = genes[g].substring(genes[g].indexOf("<k>") + 3, genes[g].indexOf("</k>"));
				if (name.length() == 0) {
					continue;
				}
				ei.add(new ExtraImage(name, regex, method, sigma, k));
			}
			stream.close();
			fc.close();
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
		return ei;
	}

	private void batch() {
		try {
			JFileChooser fc = new JFileChooser(path);
			fc.setDialogTitle("Directory...");
			fc.setApproveButtonText("Select");
			fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			fc.setSelectedFile(new File(path));
			if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				path = fc.getSelectedFile().getAbsolutePath();
				Prefs.set("FLS_Ace.path", path);
			} else {
				return;
			}
			File inputFile = new File(path);
			boolean gotExp = false;

			if (inputFile.getName().matches(expRegex)) {
				new Batch(inputFile, 0, true).run();
				gotExp = true;
			} else {
				File[] files = inputFile.listFiles();
				ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
				int pos = 0;
				for (int f = 0; f < files.length; f++) {
					if (files[f].isDirectory() && files[f].getName().matches(expRegex)) {
						final File expDir = files[f];
						Batch job = new Batch(expDir, pos, true);
						exec.submit(job);
						gotExp = true;
					}
				}
				exec.shutdown();
			}
			if (!gotExp) {
				IJ.error("No experiments found", path + " does not contain any experiment directories.");
			}
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	public class Batch implements Runnable {
		private File file;
		private int pos;
		private ArrayList<ResultsTable> tables;
		private String json;
		private boolean show_results_windows;

		public Batch(File file, int pos, boolean show_results_windows) {
			try {
				this.file = file;
				this.pos = pos;
				this.tables = new ArrayList<ResultsTable>();
				this.show_results_windows = show_results_windows;
			} catch (Exception e) {
				IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
			}
		}

		private int getTimeFromString(String str) {
			String t = "";
			int time = 0;
			try {
				Matcher matcher = timeRegex.matcher(str);
				while (matcher.find()) {
					t = str.substring(matcher.start() + 1, str.length()); // find start of last match
				}
			} catch (Exception e) {
				IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
			}
			try {
				time = Integer.valueOf(t);
			} catch (NumberFormatException nfe) {
				IJ.log(nfe.toString() + "\n" + str + "#" + t + "#" + time);
			}
			return time;
		}

		public void run() {
			try {
				ArrayList<ExtraImage> extra = getExtraConfig();
				String filePath = file.getAbsolutePath();
				File[] exp = new File(filePath).listFiles();
				ArrayList<ImagePlus> actinList = new ArrayList<ImagePlus>();
				int maxT = 1;
				boolean gotT = false;
				for (int e = 0; e < exp.length; e++) {
					String expPath = exp[e].getAbsolutePath();
					if (exp[e].isDirectory() && expPath.matches(".*t[0-9]{1,2}")) {
						File[] time = new File(expPath).listFiles();
						for (int t = 0; t < time.length; t++) {
							String timePath = time[t].getAbsolutePath();
							if (timePath.matches(".*actin\\.TIF")) {
								gotT = true;
								ImagePlus actin = IJ.openImage(timePath);
								int tin = getTimeFromString(expPath);
								maxT = Math.max(maxT, tin);
								actin.setTitle("actin-t" + tin);
								Calibration cal = new Calibration();
								cal.setUnit("\u00b5m");
								cal.setTimeUnit("sec");
								cal.frameInterval = tCal;
								cal.pixelWidth = voxelW;
								cal.pixelHeight = voxelW;
								cal.pixelDepth = voxelD;
								actin.setCalibration(cal);
								actinList.add(actin);
							}
						}
					}
				}
				if (!gotT) {
					IJ.error("No frames found", filePath + " does not contain any frame directories.");
					return;
				}
				for (int i1 = 0; i1 < actinList.size(); i1++) {
					int t1 = getTimeFromString(actinList.get(i1).getTitle());
					for (int i2 = i1 + 1; i2 < actinList.size(); i2++) {
						int t2 = getTimeFromString(actinList.get(i2).getTitle());
						if (t1 > t2) {
							Collections.swap(actinList, i1, i2);
							i1 = 0;
							break;
						}
					}
				}

				Concatenator conc = new Concatenator();
				ImagePlus expActin = conc.concatenate(actinList.toArray(new ImagePlus[0]), false);
				expActin.setTitle(file.getName() + "_actin");
				ArrayList<FLS>[] flss = FLSMapper.map(expActin, actinSigma, actinK, actinMethod, base, minLength,
						maxDist);

				for (int t = 0; t < maxT; t++) {
					if (flss[t] == null) {
						continue;
					}
					for (FLS fls : flss[t]) {
						fls.expPath = new File(filePath);
					}

					String ePath = filePath + foo + "t" + (t + 1) + foo;
					File[] eFiles = new File(ePath).listFiles();

					ShapeRoi bgroi = new ShapeRoi(new Roi(0, 0, expActin.getWidth(), expActin.getHeight()));
					for (FLS fls : flss[t]) {
						bgroi = bgroi.not(new ShapeRoi(RoiScaler.scale(fls.base, 2, 2, true)));
					}

					for (FLS fls : flss[t]) {
						ShapeRoi localbg = new ShapeRoi(RoiScaler.scale(fls.base, 3, 3, true));
						localbg = localbg.not(new ShapeRoi(fls.base)).and(bgroi);
						fls.localbg = localbg;
						expActin.setPosition(1, base, t + 1);
						expActin.setRoi(localbg);
						ImageStatistics stats = expActin.getStatistics();
						fls.localActinBackgroundMean = stats.mean;
						fls.localActinBackgroundStd = stats.stdDev;
					}

					for (ExtraImage e : extra) {
						for (int k = 0; k < eFiles.length; k++) {
							if (eFiles[k].getAbsolutePath().matches(".*" + e.regex + ".TIF")) {
								// IJ.log("got "+e.name+" for t"+t);
								ImagePlus eimp = IJ.openImage(eFiles[k].getAbsolutePath());
								e.image = eimp;
								ImagePlus eimpMask = e.mask(eimp);
								for (FLS fls : flss[t]) {
									eimpMask.setRoi(fls.base);
									ImageStatistics stats = eimpMask.getStatistics();
									fls.addGeneFraction(e.name, stats.mean / 255d);
									eimp.setRoi(fls.base);
									stats = eimp.getStatistics();
									fls.addGeneMean(e.name, stats.mean);

									ShapeRoi localbg = new ShapeRoi(RoiScaler.scale(fls.base, 3, 3, true));
									localbg = localbg.not(new ShapeRoi(fls.base)).and(bgroi);
									eimp.setRoi(localbg);
									stats = eimp.getStatistics();
									fls.addGeneBackground(e.name, stats.mean, stats.stdDev);
									// IJ.log(e.name+" : "+stats.mean);
								}
								if (e.name.matches("actin-TIRF")) {
									IJ.run(eimpMask, "Create Selection", "");
									if (eimpMask.getRoi() == null) {
										continue;
									}
									Roi[] split = new ShapeRoi(eimpMask.getRoi()).getRois();
									if (split.length == 0) {
										continue;
									}
									IJ.run(eimpMask, "Select None", "");
									for (int i = 0; i < split.length; i++) {
										eimp.setRoi(split[i]);
										Rectangle rect = split[i].getBounds();
										ImageStatistics tirfStats = eimp.getStatistics();
										for (FLS fls : flss[t]) {
											if (fls.contains(rect.x, rect.y)) {
												fls.setTIRFStats(split[i], tirfStats);
												break;
											}
										}
									}
								}

								// ImagePlus e_o_imp =
								// IJ.openImage(eFiles[k].getAbsolutePath().replaceFirst(".TIF",
								// "_original.TIF"));
								// e_o_imp.setRoi(bgroi);
								// ImageStatistics stats = e_o_imp.getStatistics();
								// double background_mean = stats.mean;
								// double background_std = stats.stdDev;
								// for(FLS fls:flss[t]){
								// fls.addGeneBackground(e.name,background_mean, background_std);
								// }
								// e_o_imp.close();

								eimp.close();
								eimpMask.close();
							}
						}
					}

					expActin.setPosition(1, base, t + 1);
					expActin.setRoi(bgroi);
					ImageStatistics stats = expActin.getStatistics();
					double background_mean = stats.mean;
					double background_std = stats.stdDev;
					for (FLS fls : flss[t]) {
						fls.actinBackgroundMean = background_mean;
						fls.actinBackgroundStd = background_std;
					}
				}
				final FLSOutput ro = new FLSOutput(expActin, file.getName(), tCal, flss, extra, base, voxelW);
				this.tables.add(ro.table(this.show_results_windows));
				this.tables.add(ro.trace(this.show_results_windows));
				this.json = ro.json();
				if (save) {
					ro.overlay(expActin);
					IJ.saveAs(expActin, "Tiff", path + foo + file.getName() + "_rois.tif");
					expActin.close();
				}
			} catch (Exception e) {
				IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
			}
		}

		public ArrayList<ResultsTable> get_tables() {
			return this.tables;
		}

		public String get_json() {
			return this.json;
		}
	}

	private void createDefaultConfig() {
		try {
			File configFile = new File(System.getProperty("user.home") + foo + "FLS_Ace_config.xml");
			InputStream stream = getClass().getResourceAsStream("default_config.xml");
			BufferedReader br = new BufferedReader(new InputStreamReader(stream));
			String template = "";
			String str = "";
			while ((str = br.readLine()) != null) {
				template += str + System.getProperty("line.separator");
			}
			stream.close();
			br.close();

			BufferedWriter bw = new BufferedWriter(new FileWriter(configFile, false));
			bw.write(template);
			bw.close();
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	public void actionPerformed(ActionEvent ae) {
		try {
			String event = ae.getActionCommand();
			base = (Integer) baseSpinner.getValue();
			try {
				voxelW = Double.valueOf(Wfield.getText());
			} catch (NumberFormatException nfe) {
				IJ.error(Wfield.getText() + " is not a number");
				return;
			}
			try {
				voxelD = Double.valueOf(Dfield.getText());
			} catch (NumberFormatException nfe) {
				IJ.error(Dfield.getText() + " is not a number");
				return;
			}
			try {
				tCal = Double.valueOf(Tfield.getText());
			} catch (NumberFormatException nfe) {
				IJ.error(Tfield.getText() + " is not a number");
				return;
			}
			try {
				actinSigma = Double.valueOf(sigmaField.getText());
			} catch (NumberFormatException nfe) {
				IJ.error(sigmaField.getText() + " is not a number");
				return;
			}
			try {
				actinK = Double.valueOf(kField.getText());
			} catch (NumberFormatException nfe) {
				IJ.error(kField.getText() + " is not a number");
				return;
			}
			try {
				minLength = Double.valueOf(lengthField.getText());
			} catch (NumberFormatException nfe) {
				IJ.error(lengthField.getText() + " is not a number");
				return;
			}
			try {
				maxDist = Double.valueOf(distField.getText());
			} catch (NumberFormatException nfe) {
				IJ.error(distField.getText() + " is not a number");
				return;
			}
			actinMethod = methods[methodCombo.getSelectedIndex()];
			save = saveTick.isSelected();
			Prefs.set("FLS_Ace.base", base);
			Prefs.set("FLS_Ace.voxelW", voxelW);
			Prefs.set("FLS_Ace.voxelD", voxelD);
			Prefs.set("FLS_Ace.tCal", tCal);
			Prefs.set("FLS_Ace.actinSigma", actinSigma);
			Prefs.set("FLS_Ace.actinK", actinK);
			Prefs.set("FLS_Ace.actinMethod", actinMethod);
			Prefs.set("FLS_Ace.minLength", minLength);
			Prefs.set("FLS_Ace.save", save);
			Prefs.set("FLS_Ace.maxDist", maxDist);
			if (event == "current") {
				current();
			} else if (event == "batch") {
				gui.dispose();
				batch();
			} else if (event == "config") {
				File configFile = new File(System.getProperty("user.home") + foo + "FLS_Ace_config.xml");
				if (!configFile.exists()) {
					createDefaultConfig();
				}
				java.awt.Desktop.getDesktop().open(configFile);
			}
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	public static void main(String[] arg) {
		ImageJ.main(arg);
		
		ImagePlus img = new ImagePlus("C:\\Users\\USER\\work\\data\\Kazimir\\Actin.TIF");
		final ImagePlus image = HyperStackConverter.toHyperStack(img, img.getNChannels(), img.getNSlices(), img.getNFrames());
		image.setDisplayMode(IJ.GRAYSCALE);
		
		image.show();
		new FLS_Ace().run();
	}

}
