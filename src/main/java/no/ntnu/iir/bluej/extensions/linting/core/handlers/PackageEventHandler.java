package no.ntnu.iir.bluej.extensions.linting.core.handlers;

import bluej.extensions2.BClass;
import bluej.extensions2.BPackage;
import bluej.extensions2.event.PackageEvent;
import bluej.extensions2.event.PackageListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import no.ntnu.iir.bluej.extensions.linting.core.checker.ICheckerService;
import no.ntnu.iir.bluej.extensions.linting.core.ui.AuditWindow;
import no.ntnu.iir.bluej.extensions.linting.core.violations.ViolationManager;

/**
 * Represents a Package event handler.
 * Responsible for listening to Package Events within BlueJ.
 * Spawns a new window every time a project is opened.
 */
public class PackageEventHandler implements PackageListener {
  private HashMap<String, AuditWindow> projectWindowMap;
  private String windowTitlePrefix;
  private ViolationManager violationManager;
  private ICheckerService checkerService;

  /**
   * Instantiates a new handler for Package events.
   * 
   * @param windowTitlePrefix the title prefix of audit windows spawned
   * @param violationManager the ViolationManager for windows to subscribe to
   */
  public PackageEventHandler(
      String windowTitlePrefix, 
      ViolationManager violationManager, 
      ICheckerService checkerService
  ) {
    this.projectWindowMap = new HashMap<>();
    this.windowTitlePrefix = windowTitlePrefix;
    this.violationManager = violationManager;
    this.checkerService = checkerService;
  }

  /**
   * Handles packageClosing event within BlueJ.
   * Removes the the closed package/projects AuditWindow from map and closes it.
   */
  @Override
  public void packageClosing(PackageEvent packageEvent) {
    try {
      String packagePath = packageEvent.getPackage().getDir().getPath();
      this.violationManager.removeBluePackage(packageEvent.getPackage());
      AuditWindow projectWindow = this.projectWindowMap.get(packagePath);
      if (projectWindow != null && projectWindow.isShowing()) {
        this.violationManager.removeListener(projectWindow);
        this.projectWindowMap.remove(packagePath);
        projectWindow.close();
      }
    } catch (Exception e) {
      // TODO: Proper Exception handling should be done here
      e.printStackTrace();
    }
  }

  /**
   * Handles a packageOpened event within BlueJ.
   */
  @Override
  public void packageOpened(PackageEvent packageEvent) {
    try {
      String packagePath = packageEvent.getPackage().getDir().getPath();
      AuditWindow projectWindow = new AuditWindow(
          windowTitlePrefix, 
          packageEvent.getPackage(), 
          packagePath
      );
      
      this.violationManager.addBluePackage(packageEvent.getPackage());
      this.violationManager.addListener(projectWindow);
      this.projectWindowMap.put(packagePath, projectWindow);
      this.checkerService.enable();
      this.checkAllPackageFiles(packageEvent.getPackage());

      projectWindow.show();
    } catch (Exception e) {
      // TODO: Proper Exception handling should be done here
      e.printStackTrace();
    }
  }

  private void checkAllPackageFiles(BPackage bluePackage) {
    try {
      BClass[] classes = bluePackage.getClasses();
      List<File> filesToCheck = new ArrayList<>();
  
      for (int i = 0; i < classes.length; i++) {
        filesToCheck.add(classes[i].getJavaFile());
      }

      this.checkerService.checkFiles(filesToCheck, "utf-8");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
