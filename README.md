# Android-File-Downloader
Add any link to parallel download based on settings, it is little similar to DownloadManager

# Features
1. User can change parallel download settings from menu option
2. User can see notification with download progress, speed, time left also 2 buttons Pause/Resume and Cancel
3. User can selected desired folder where the file will be saved
4. User can see complete progress of item
   1. Waiting in queue
   2. Waiting for network
   3. Waiting for WiFi (only when download item is set over WiFi)
   4. All downloaded items
   5. Failed items
   6. Live progress for in-progress items
5. User can pause item by clicking on item
6. User can resume item by clicking on item
7. User can cancel item any time by clicking on item
8. User can restart failed item by clicking on item
9. User can go to menu and pause all downloading item(s)
10. User can go to menu and resume all downloading item(s)

# Notification
    - Uses foreground notificartion
    - Live update of downlaoding progress
    - Live update of downlaoding speed
    - Live update of downlaoding ETA (estimated time of arrival)
    - Pause/Resume button to control the downloadng item
    - Cancel button to cancel the downloadng/paused item

# Background Download
    - Even the app is closed, download continues 
    - User can see and control downlods via Notification 