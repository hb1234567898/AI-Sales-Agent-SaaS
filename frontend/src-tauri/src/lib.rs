use tauri::Manager;

#[cfg(target_os = "windows")]
const TASKBAR_ICON: tauri::image::Image<'static> = tauri::include_image!("icons/taskbar-dark.png");

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.set_title("AI Sales Agent");
                #[cfg(target_os = "windows")]
                window.set_icon(TASKBAR_ICON)?;
            }
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("AI Sales Agent 桌面端启动失败");
}

#[cfg(all(test, target_os = "windows"))]
mod tests {
    use super::TASKBAR_ICON;

    #[test]
    fn taskbar_icon_keeps_dark_background_and_transparent_corners() {
        assert_eq!((TASKBAR_ICON.width(), TASKBAR_ICON.height()), (128, 128));
        assert_eq!(TASKBAR_ICON.rgba()[3], 0);
        let offset = (16 * TASKBAR_ICON.width() as usize + 16) * 4;
        assert!(TASKBAR_ICON.rgba()[offset..offset + 3]
            .iter()
            .all(|&v| v < 80));
        assert!(TASKBAR_ICON.rgba()[offset + 3] >= 250);
    }
}
