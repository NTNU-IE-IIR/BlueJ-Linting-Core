package no.ntnu.iir.bluej.extensions.linting.core.handlers;

import bluej.extensions2.BPackage;
import bluej.extensions2.event.PackageEvent;
import bluej.extensions2.event.PackageListener;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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
  private ViolationManager violationManager;
  private ICheckerService checkerService;

  /**
   * Instantiates a new handler for Package events.
   * 
   * @param violationManager the ViolationManager for windows to subscribe to
   * @param checkerService the CheckerService to use for checking files when a package opens
   */
  public PackageEventHandler(
      ViolationManager violationManager, 
      ICheckerService checkerService
  ) {
    this.projectWindowMap = new HashMap<>();
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
      // should never happen, package/project should be open when this is called by BlueJ    
    }
  }

  /**
   * Handles a packageOpened event within BlueJ.
   */
  @Override
  public void packageOpened(PackageEvent packageEvent) {
    this.openProjectWindow(packageEvent.getPackage());
  }

  /**
   * Opens a new AuditWindow for a Project.
   * 
   * @param bluePackage the package to open a window for
   */
  public void openProjectWindow(BPackage bluePackage) {
    try {
      String packagePath = bluePackage.getDir().getPath();
      AuditWindow projectWindow = projectWindowMap.get(packagePath);

      if (projectWindow == null) {
        
        String parentKey = this.findRootPackageKey(packagePath);

        if (parentKey != null) {
          this.violationManager.addBluePackage(bluePackage);
        } else {
          projectWindow = new AuditWindow(
              bluePackage, 
              packagePath
          );
          
          this.projectWindowMap.put(packagePath, projectWindow);
          this.violationManager.addListener(projectWindow);
          List.of(bluePackage.getProject().getPackages()).forEach(
              this.violationManager::addBluePackage
          );
          this.checkerService.enable();
          this.violationManager.syncBlueClassMap();
        }
        
        PackageEventHandler.checkAllPackagesOpen(this.violationManager, this.checkerService);

      }

    } catch (Exception e) {
      // should never happen, package/project should be open when this is called by BlueJ
    }
  }

  /**
   * Shows the project window from a BlueJ package.
   * 
   * @param bluePackage the package to show project window for
   */
  public void showProjectWindow(BPackage bluePackage) {
    try {
      String packagePath = bluePackage.getDir().getPath();
      String projectKey = this.findRootPackageKey(packagePath);
      AuditWindow projectWindow = this.projectWindowMap.get(projectKey);

      if (projectWindow != null) {
        projectWindow.show();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Checks if there is a root package open, if one is open it returns its key.
   * 
   * @param packagePath the path of the BlueJ package
   * @return the root package key, or null if non-existant
   */
  private String findRootPackageKey(String packagePath) {
    Iterator<String> pathIterator = this.projectWindowMap.keySet().iterator();
    String parentWindowKey = null;
    boolean hasOpenParent = false;

    while (pathIterator.hasNext() && !hasOpenParent) {
      String currentPath = pathIterator.next();
      if (packagePath.startsWith(currentPath)) {
        hasOpenParent = true;
        parentWindowKey = currentPath;
      }
    }

    return parentWindowKey;
  }

  /**
   * Handles checking all the open packages/projects.
   * 
   * @param violationManager the ViolationManager to clear old violations from
   * @param checkerService the CheckerService implementation to call for new checks
   */
  public static void checkAllPackagesOpen(
      ViolationManager violationManager, 
      ICheckerService checkerService
  ) {
    try {
      List<BPackage> bluePackages = violationManager.getBluePackages();
      violationManager.clearViolations();
      for (BPackage bluePackage : bluePackages) {
        List<File> filesToCheck = List.of(bluePackage.getClasses())
            .stream()
            .map(blueClass -> {
              File sourceFile = null;

              try {
                // ignore non-compiled files
                if (blueClass.isCompiled()) {
                  sourceFile = blueClass.getJavaFile();
                }
              } catch (Exception e) {
                // ignore
              }

              return sourceFile;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        checkerService.checkFiles(filesToCheck, "utf-8");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
