package no.ntnu.iir.bluej.extensions.linting.core.editor;

import bluej.extensions2.BClass;
import bluej.extensions2.PackageNotFoundException;
import bluej.extensions2.ProjectNotOpenException;
import bluej.extensions2.editor.JavaEditor;
import bluej.extensions2.editor.TextLocation;
import no.ntnu.iir.bluej.extensions.linting.core.violations.Violation;

/**
 * Represents a utility class for notifying the BlueJ editor.
 * Responsible for communicating with the BlueJ editor and displaying
 * errors/red lines under a TextLocation and highlighting text on request.
 */
public class EditorNotifier {
  private EditorNotifier() {}

  /**
   * Highlights a line in a file in the BlueJ editor.
   * Fetches the JavaEditor proxy or opens a new one.
   * Sets the JavaEditor to be visible, and then sets the selection.
   * 
   * @param violation the source violation to highlight in the editor
   * 
   * @throws ProjectNotOpenException if the BCLass' project was not open
   * @throws PackageNotFoundException if the BClass' package was not found
   */
  public static void highlightLine(
      Violation violation
  ) throws ProjectNotOpenException, PackageNotFoundException {
    BClass blueClass = violation.getBClass();
    TextLocation textLocation = violation.getLocation();
    JavaEditor fileEditor = blueClass.getJavaEditor();

    int lineNumber = textLocation.getLine() - 1;
    int lineLength = fileEditor.getLineLength(textLocation.getLine() - 1);
    int startColumn = (textLocation.getColumn() > 0) ? textLocation.getColumn() - 1 : 1;

    fileEditor.setVisible(true);
    fileEditor.setSelection(
        new TextLocation(lineNumber, startColumn),
        new TextLocation(lineNumber, lineLength)
    );
  }
}
