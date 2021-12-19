package no.ntnu.iir.bluej.extensions.linting.core.violations;

import java.util.HashMap;
import java.util.List;

/**
 * Represents a Listener interface for Violations.
 * Should be implemented by listeners of the ListenerManager class.
 */
public interface ViolationListener {
  void onViolationsChanged(HashMap<String, List<Violation>> violations);
}