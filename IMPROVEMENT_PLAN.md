# Improvement Plan - Settings and GSS-API Support

## 1. GUI Modernization
The current settings window is functional but cluttered. The "Connections" tab uses a top-bottom split which limits space for both the connection list and the editing fields.

### Proposed Changes:
- **Layout Redesign**: Switch from a top-bottom split to a left-right split.
    - **Left Side**: A `ListView` or a simplified `TableView` containing only connection names and tags.
    - **Right Side**: A structured form (using `GridPane`) for all connection properties.
- **Improved Spacing**: Add proper padding and gaps between controls to avoid the "cramped" feel.
- **Grouping**: Group related settings together (e.g., "Server Settings", "Authentication", "LDAP Settings").
- **Visual Feedback**: Use icons for buttons (ADD, DELETE, SAVE, etc.) to make the UI more intuitive.

## 2. GSS-API (Kerberos) Support
Currently, only Simple Bind is supported. AD LDS and AD often require GSS-API for secure authentication.

### Proposed Changes:
- **Add GSS-API Option**: Add a `Use GSS-API` checkbox to the connection settings.
- **Update Connection Logic**:
    - Integrate `com.unboundid.ldap.sdk.GSSAPIBindRequest` in `Connection.connect()`.
    - Support GSS-API over SSL (LDAPS) or with StartTLS.
    - Allow optional configuration for Kerberos realm/KDC if needed, or default to system-level Kerberos configuration.
- **Fix SSL/GSS-API Conflict**: Ensure the `Use SSL` flag can be used concurrently with `GSS-API`.

## 3. Event and Logic Fixes
- **Selection Handling**: Ensure that when a connection is selected, all fields are correctly populated and the "SAVE" button state is managed properly.
- **CSV Export**: Fix the header formatting in CSV export (missing semicolons).
- **Control States**: Implement better management of button enabled/disabled states based on whether a connection is being edited, newly created, or selected.
- **Password Handling**: Improve how passwords are shown/hidden and copied to clipboard.

## 4. Implementation Steps
1.  **Modify `Connection` class**: Add `useGSSAPI` property.
2.  **Modify `Config.kt`**: Add persistence for `useGSSAPI`.
3.  **Update `Connection.connect()`**: Implement GSS-API bind logic.
4.  **Redesign `Settings.fxml`**: Implement the new left-right layout.
5.  **Update `SettingsController.java`**: Wire up the new UI components and logic.
6.  **Testing**: Verify GSS-API connection with AD/AD LDS.
