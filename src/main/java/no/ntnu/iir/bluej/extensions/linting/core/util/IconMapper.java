package no.ntnu.iir.bluej.extensions.linting.core.util;

import java.net.URL;

/**
 * Represents a interface for mapping Strings to Icon URLs.
 * Used for mapping icons to violation severity and type.
 */
public interface IconMapper {

  /**
   * Get the mapped icon for a given String.
   * 
   * @param name the name of the Icon
   * 
   * @return a URL to the mapped icon, null if none was found
   */
  URL getIcon(String name);
}
