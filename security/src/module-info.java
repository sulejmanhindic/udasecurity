module security {
    requires image.src.main.java.com.udacity.catpoint;
    opens security.com.udacity.catpoint.data to com.google.gson;
}