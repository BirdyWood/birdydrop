# BirdyDrop

BirdyDrop is an Android library for sharing data using Bluetooth Low Energy (BLE).  
It enables seamless and efficient data exchange between devices using BLE, making it ideal for peer-to-peer communication and proximity-based sharing in Android apps.

## Features

- Simple integration as an Android library
- Fast peer-to-peer data sharing over BLE
- Minimal permissions required
- Optimized for low energy consumption

## Getting Started

These instructions will help you set up and use BirdyDrop in your Android project.

### Prerequisites

- Android Studio (recommended)
- Minimum SDK version: *24+*
- Bluetooth Low Energy support on device

### Installation

1. Download the AAR library file from [Releases](https://github.com/BirdyWood/birdydrop/releases/latest)
2. Add BirdyDrop to your `build.gradle` dependencies:
    ```gradle
    dependencies {
        implementation(files( [AAR_FILE_PATH] ))
    }
    ```
   > Replace `[AAR_FILE_PATH]` with the path of AAR file downloaded.

3. Sync your project with Gradle files.

### Usage

1. Initialize BirdyDrop in your application or activity:
    ```kotlin
    val birdyDrop = ApiBirdydrop(this, object : BirdydropUpdateListener {
            override fun onReceiveInfo(info: String) {

            }

            override fun onNewDevice(devices: List<DeviceBluetooth>) {
   
            }
        })
    ```

2. Start service:
    ```kotlin
    birdyDrop.onStart()
    ```

3. Share data:
    ```kotlin
    birdyDrop.sendMsg("Hello world")
    ```

4. Initialize Birdydrop UI:
    ```kotlin
   BirdyDrop(birdyDrop)
   { msg: String ->
   /* Example to open URL when receiving link */
      val p = Patterns.WEB_URL
      val m = p.matcher(msg)
      if (m.matches()){ //It's an URL
          val browserIntent = Intent(
              Intent.ACTION_VIEW, Uri.parse(msg)
          )
          startActivity(browserIntent)
      }else{
          // Do something else
      }
   }
    ```
   
## Contributing

Contributions are welcome! Please open issues or submit pull requests for improvements and fixes.

1. Fork the repository
2. Create a new branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -am 'Add new feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a pull request

## License

[Specify your license here, e.g., MIT, Apache 2.0, GPL, etc.]

---

*For questions or support, please contact the maintainer or open an issue on GitHub.*