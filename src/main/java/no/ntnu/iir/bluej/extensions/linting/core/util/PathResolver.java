package no.ntnu.iir.bluej.extensions.linting.core.util;

import bluej.extensions2.BPackage;
import bluej.extensions2.ProjectNotOpenException;

import java.nio.file.FileSystems;

public class PathResolver {
  
  private PathResolver() {}

  /**
   * Resolves the project path of a given BPackage.
   * 
   * @param bluePackage the BPackage to resolve the project directory path for
   * 
   * @return the path of the project, including a separator at the end
   * 
   * @throws ProjectNotOpenException if the project is not open
   */
  public static final String projectPath(BPackage bluePackage) throws ProjectNotOpenException {
    return bluePackage.getProject().getDir().getPath() + FileSystems.getDefault().getSeparator();
  }

}
