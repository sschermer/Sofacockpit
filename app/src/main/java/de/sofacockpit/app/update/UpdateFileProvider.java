package de.sofacockpit.app.update;

/** Dedicated provider; only the private update-cache path is shared. */
public final class UpdateFileProvider extends androidx.core.content.FileProvider {
    public UpdateFileProvider() { super(); }
}
