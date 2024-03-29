package no.ntnu.iir.bluej.extensions.linting.core.ui;

import bluej.extensions2.BPackage;
import bluej.extensions2.ProjectNotOpenException;

import java.util.HashMap;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import no.ntnu.iir.bluej.extensions.linting.core.violations.Violation;
import no.ntnu.iir.bluej.extensions.linting.core.violations.ViolationListener;

/**
 * Represents a GUI Window responsible for displaying linter violations.
 * This is the main window for the Linter extensions.
 * Listens to the ViolationListener, and updates the GUI when violations change.
 */
public class AuditWindow extends Stage implements ViolationListener {
  private VBox vbox;
  private String projectDirectory;
  private Label defaultLabel;
  private RuleWebView ruleWebView;
  private PauseTransition debouncer;
  
  // the time in milliseconds to debounce the update render
  private static final int DEBOUNCE_DELAY = 100;

  private static String titlePrefix = "AuditWindow";
  private static HBox statusBar = new HBox();

  /**
   * Constructs a new AuditWindow. 
   * 
   * @param bluePackage the blueJ package to open a window for
   * @param projectDirectory the project directory this auditwindow belongs to
   * 
   * @throws ProjectNotOpenException if the bluePackages project was not open
   */
  public AuditWindow(
      BPackage bluePackage, 
      String projectDirectory
  ) throws ProjectNotOpenException {
    super();

    this.projectDirectory = projectDirectory;

    String formattedTitle = String.format(
        "%s - %s", 
        titlePrefix, 
        bluePackage.getProject().getName()
    );

    this.debouncer = new PauseTransition(Duration.millis(DEBOUNCE_DELAY));
    this.setTitle(formattedTitle);
    this.initScene();
  }

  /**
   * Initiates the AuditWindow scene.
   * Responsible for instantiating panes and setting default content.
   */
  private void initScene() {
    this.vbox = new VBox();

    // display a default label indicating no violations was found
    defaultLabel = new Label("No violations found in this project");
    defaultLabel.setPadding(new Insets(6));

    Separator separator = new Separator(Orientation.HORIZONTAL);
  
    this.vbox.getChildren().addAll(statusBar, separator, defaultLabel);

    ScrollPane violationsPane = new ScrollPane();
    violationsPane.setContent(this.vbox);
    violationsPane.setFitToWidth(true);

    // set up web view for rules and set default content
    this.ruleWebView = new RuleWebView();
    this.ruleWebView.setContent("Select a rule to see its explanation here...");
    RuleWebView.setStylesheet(
        "data:,body { font: 12px Segoe UI, Arial; }"
    );

    // bind webview to the size of the pane
    ScrollPane rulePane = new ScrollPane();
    this.ruleWebView.getWebView().prefWidthProperty().bind(rulePane.widthProperty());
    this.ruleWebView.getWebView().prefHeightProperty().bind(rulePane.heightProperty());
    rulePane.setContent(this.ruleWebView.getWebView());

    SplitPane splitPane = new SplitPane();
    splitPane.setDividerPositions(0.8f); // violations should by default take up 80%
    splitPane.setOrientation(Orientation.VERTICAL);
    splitPane.getItems().addAll(violationsPane, rulePane);

    Scene scene = new Scene(splitPane, 750, 500);
    this.setScene(scene);
  }

  /**
   * Update the Violations pane to reflect the new truth of violations.
   * Adds a new TitledPane for each entry, containing a ListView of Violations.
   */
  private void updateViolations(HashMap<String, List<Violation>> violationsMap) {
    this.vbox.getChildren().clear();
    this.vbox.getChildren().addAll(statusBar, new Separator(Orientation.HORIZONTAL));
    if (violationsMap.size() == 0) {
      this.vbox.getChildren().addAll(this.defaultLabel);
    } else {
      violationsMap.forEach((filePath, violations) -> {
        // only add to window if it's source is from the correct project
        if (filePath.startsWith(this.projectDirectory)) {
          ListView<Violation> violationList = new ListView<>();
          violationList.setCellFactory(violation -> new ViolationCell(
              this.ruleWebView
          ));

          violations.forEach(violationList.getItems()::add);

          // set list height to its estimated height to hide empty rows
          violationList
            .prefHeightProperty()
            .bind(Bindings.size(violationList.getItems()).multiply(24));

          String fileName = filePath.substring(this.projectDirectory.length());
          String paneTitle = String.format("%s (%s violations)", fileName, violations.size());
          TitledPane pane = new TitledPane(paneTitle, violationList);
          this.vbox.getChildren().add(pane);
        }
      });
    }
  }


  @Override
  public void onViolationsChanged(HashMap<String, List<Violation>> violationsMap) {
    // sole purpose of this is to debounce the update.
    // will prevent rendering 1 time per violation added etc
    this.debouncer.setOnFinished(e -> this.updateViolations(violationsMap));
    this.debouncer.playFromStart();
  }
  
  /**
   * Sets the windows title prefix. 
   * Typically the name of the extension/tool.
   * 
   * @param prefix the windows title prefix
   */
  public static void setTitlePrefix(String prefix) {
    AuditWindow.titlePrefix = prefix;
  }

  /**
   * Sets the statusBar to render across all AuditWindows.
   * 
   * @param statusBar the statusBar to render across all AuditWindows
   */
  public static void setStatusBar(HBox statusBar) {
    AuditWindow.statusBar = statusBar;
  }
}
