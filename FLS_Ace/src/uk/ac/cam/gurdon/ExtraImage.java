package uk.ac.cam.gurdon;

import ij.*;
import ij.plugin.*;

import java.util.Arrays;

public class ExtraImage {
	public String name;
	public ImagePlus image;
	public String regex;
	public String method;
	public double sigma;
	public double k;

	public ExtraImage(String name, ImagePlus image, String regex, String method, double sigma, double k)
			throws Exception {
		this.name = name;
		this.image = image;
		this.regex = regex;
		this.method = method;
		this.sigma = sigma;
		this.k = k;
		checkImage();
	}

	public ExtraImage(String name, String regex, String method, double sigma, double k) {
		this.name = name;
		this.regex = regex;
		this.method = method;
		this.sigma = sigma;
		this.k = k;
	}

	public ExtraImage(String name, String regex, String method, String sigma, String k) {
		try {
			this.name = name;
			this.regex = regex;
			this.method = method;
			this.sigma = Double.valueOf(sigma);
			this.k = Double.valueOf(k);
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
	}

	public ImagePlus mask(ImagePlus imp) {
		ImagePlus masked = new ImagePlus();
		try {
			masked = new Duplicator().run(imp);
			ImagePlus sub = new Duplicator().run(imp);
			IJ.run(masked, "Gaussian Blur...", "sigma=" + sigma);
			IJ.run(sub, "Gaussian Blur...", "sigma=" + sigma * k);
			new ImageCalculator().run("Subtract stack", masked, sub);
			sub.close();
			IJ.setAutoThreshold(masked, method + " dark");
			IJ.run(masked, "Convert to Mask", "method=" + method + " background=Dark black");
			IJ.run(masked, "Watershed", "stack");
			IJ.run(masked, "Open", "stack");
		} catch (Exception e) {
			IJ.log(e.toString() + "\n~~~~~\n" + Arrays.toString(e.getStackTrace()).replace(",", "\n"));
		}
		return masked;
	}

	public void setImage(ImagePlus image) throws Exception {
		this.image = image;
		checkImage();
	}

	private void checkImage() throws Exception {
		if (image == null) {
			throw new Exception("ExtraImage ImagePlus missing for " + name);
		}
		if (image.getNDimensions() > 2) {
			throw new Exception("ExtraImage should only have 2 dimensions");
		}
	}

	public String toString() {
		return name + ":" + (image != null) + " , " + regex + " , " + method + " , " + sigma + " , " + k;
	}

}
