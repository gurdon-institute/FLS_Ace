package uk.ac.cam.gurdon;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.HyperStackConverter;

@Plugin(type = Command.class, menuPath = "Plugins>MavenTemplate")
public class MavenTemplate implements Command {

	public void run() {
		System.out.println("test");
	}

	public static void main(String[] arg) {

		ImageJ.main(arg);
		/*
		 * ImagePlus img = new ImagePlus("E:\\test data\\3D_DAPI_liver.tif"); final
		 * ImagePlus image = HyperStackConverter.toHyperStack(img, img.getNChannels(),
		 * img.getNSlices(), img.getNFrames()); image.setDisplayMode(IJ.GRAYSCALE);
		 * image.setPosition(1, (int)(img.getNSlices()/2f), 1); image.show();
		 */

		new MavenTemplate().run();
	}

}
