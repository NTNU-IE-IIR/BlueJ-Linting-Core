package no.ntnu.iir.bluej.extensions.linting.core.checker;

import java.io.File;
import java.util.List;

/**
 * Represents an interface for CheckerServices.
 * Classes responsible for handling file checks should implement this interface.
 */
public interface ICheckerService {

  /**
   * Enables the CheckerService.
   */
  void enable();

  /**
   * Disables the CheckerService.
   */
  void disable();

  /**
   * Checks whether the CheckerService is enabled or not.
   * 
   * @return whether the CheckerService is enabled or not
   */
  boolean isEnabled();

  /**
   * Checks a single file.
   * 
   * @param fileToCheck the File to check.
   * @param charset the Charset of the File to check.
   * 
   * @throws Exception if an error occurs while processing the file
   */
  void checkFile(File fileToCheck, String charset) throws Exception;

  /**
   * Checks a List of files.
   * 
   * @param filesToCheck a List of Files to check.
   * @param charset the Charset of the Files to check
   * 
   * @throws Exception if an error occurs while processing files
   */
  void checkFiles(List<File> filesToCheck, String charset) throws Exception;
}
