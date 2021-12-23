package no.ntnu.iir.bluej.extensions.linting.core.handlers;

import bluej.extensions2.BClass;
import bluej.extensions2.event.ClassEvent;
import bluej.extensions2.event.ClassListener;
import no.ntnu.iir.bluej.extensions.linting.core.checker.ICheckerService;
import no.ntnu.iir.bluej.extensions.linting.core.violations.ViolationManager;

/**
 * Represents a FilesChangeHandler, listening for file changes in class files and handles them.
 * Implements BlueJ's ClassListener interface, and handles the events triggered by BlueJ.
 * Should remove old Violations from the ViolationsManager, and if necessary reprocess the file.
 */
public class FilesChangeHandler implements ClassListener {
  private ViolationManager violationManager;
  private ICheckerService checkerService;

  /**
   * Constructs a new FilesChangeHandler. 
   * 
   * @param violationManager the ViolationManager to clear/remove old violations from
   * @param checkerService the CheckerService implemenentation to call for new checks
   */
  public FilesChangeHandler(ViolationManager violationManager, ICheckerService checkerService) {
    this.violationManager = violationManager;
    this.checkerService = checkerService;
  }

  /**
   * Helper method for processing a single class file.
   * 
   * @param fileName the name of the file to check.
   * @param blueClass the BClass instance where a change was detected in.
   */
  private void processFile(String fileName, BClass blueClass) {
    this.violationManager.removeViolations(fileName);
    try {
      // only check the file if it has compiled
      if (blueClass.isCompiled()) {
        this.checkerService.checkFile(blueClass.getJavaFile(), "utf-8");
      }
    } catch (Exception e) {
      // this should never happen, BlueJ project/package has to be existant
      // in order for BlueJ to trigger the events that uses this method.
    }
  }

  /**
   * Removes violations for the old file name and reprocess with new name.
   */
  @Override
  public void classNameChanged(ClassEvent classEvent) {
    this.processFile(classEvent.getOldName(), classEvent.getBClass());    
  }

  /**
   * Removes violations for the removed class file.
   */
  @Override
  public void classRemoved(ClassEvent classEvent) {
    this.violationManager.removeViolations(classEvent.getOldName());
  }

  /**
   * Reprocesses the changed class file.
   */
  @Override
  public void classStateChanged(ClassEvent classEvent) {
    BClass blueClass = classEvent.getBClass();
    this.processFile(blueClass.getName(), blueClass);
  }
  
}
