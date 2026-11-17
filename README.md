# BOOX Go 7 中文語音翻頁
版本 0.2.1。Android 13 / BOOX Go 7 離線中文語音翻頁：說「下一頁／上一頁」以無障礙服務模擬左右滑動。免 Tasker、AutoVoice，不要求網路權限、不讀取其他 App 文字。

## GitHub Actions 編譯
Actions → BOOX Android APK build → 成功的執行紀錄 → Artifacts → BOOX-Go7-Chinese-VoicePage-APK。下載 ZIP 並解壓得到 app-debug.apk。

## BOOX 安裝
1. 將 APK 複製到 BOOX Go 7 並授權安裝未知來源 App。
2. 開啟本 App →「① 開啟無障礙設定」→ 啟用「BOOX 語音翻頁手勢」。Android 13 若顯示「受限制的設定」，先在 App 資訊頁右上角允許受限制的設定。
3. 回本 App →「② 開始離線聆聽」，授權麥克風。
4. 切回閱讀 App，說「下一頁」或「上一頁」。通知列可停止。
5. 在 BOOX 最佳化設定中允許背景活動、避免凍結；仍須 BOOX 實機測試續航與穩定性。

## 測試與風險
GitHub Actions 編譯通過不等於 Android 模擬器或 BOOX 實機通過。Vosk 小型中文模型首次啟動須解壓到 App 私有儲存空間，長時間收音會耗電。正式安裝及語音指令辨識仍需 Go 7 真機核對。

## 授權與來源
Vosk API 與 vosk-model-small-cn-0.22 中文模型為 Apache-2.0。
https://github.com/alphacep/vosk-api
https://alphacephei.com/vosk/models
