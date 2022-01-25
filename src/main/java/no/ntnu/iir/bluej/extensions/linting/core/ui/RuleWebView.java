package no.ntnu.iir.bluej.extensions.linting.core.ui;

import java.awt.Desktop;
import java.net.URI;
import javax.swing.ScrollPaneConstants;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Represents a wrapper around WebView.
 * Responsible for streamlining functionality for WebViews.
 */
public class RuleWebView {
  private static String stylesheet = "data:,body { font: 12px 'Segoe UI', sans-serif; }";
  private WebView webView;
  private String content;

  public RuleWebView() {
    this.initWebView();
  }

  /**
   * Sets up the WebView using the set content.
   */
  private void initWebView() {
    this.webView = new WebView();
    this.setContent(this.content);
    this.getWebEngine().setUserStyleSheetLocation(RuleWebView.stylesheet);
    this.getWebEngine().locationProperty().addListener(this::handleLocationChanged);
  }

  /**
   * Handles when the location is changed. 
   * Responsible for preventing navigation to external pages, and opening them in the browser instead.
   * 
   * @param obs the ObservableValue changed
   * @param oldValue the previous value
   * @param newValue 
   */
  private void handleLocationChanged(ObservableValue<? extends String> obs, String oldValue, String newValue) {
    // newValue is "" when loading a rule
    // oldValue guard to prevent from opening multiple webpages
    if (!newValue.equals("") && oldValue.equals("")) {
      try {
        // not the cleanest way to remove the view but should
        // work with the current uses
        if (this.webView.getParent().getParent().getParent() instanceof ScrollPane) {
          ScrollPane parent = (ScrollPane) this.webView.getParent().getParent().getParent();
          this.initWebView();
          parent.setContent(this.webView);
        } else {
          Pane parent = ((Pane) this.webView.getParent());
          parent.getChildren().remove(this.webView);
          this.initWebView();
          parent.getChildren().add(this.webView);
        }
        Desktop desktop = Desktop.getDesktop();
        desktop.browse(new URI(newValue));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Returns the WebView.
   * 
   * @return the WebView
   */
  public WebView getWebView() {
    return this.webView;
  }

  /**
   * Returns the WebViews Engine.
   * 
   * @return the WebViews Engine
   */
  public WebEngine getWebEngine() {
    return this.webView.getEngine();
  }

  public void setContent(String content) {
    this.content = content;
    this.getWebEngine().loadContent(this.content, "text/html");
  }

  public static void setStylesheet(String stylesheet) {
    if (stylesheet != null) {
      RuleWebView.stylesheet = stylesheet;
    }
  }
}
