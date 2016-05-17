# idt programming test

## How to use

1. Start the application
2. Enter image url (Autocomplete suggests previously used URLs)
3. Press GO or enter on the keyboard
4. Image will be downloaded and saved on the device(Rotated 180 degrees) 
5. Image is displayed on the screen
6. Stored images can be found in "idt" folder in the device

## Code details (The WHY)
There are three Java classes

1. Activity

This displays and handles the events on the UI with EditText, Button and ImageView. 

2. Service 

Images are loaded in an Android Service. Service gets a URL for the image and starts an AsycTask to download the image. Once the download is finished service broadcasts the message to the Activity.

3. ImageUtil

This class is used to store and read the image from the device.







Additionally All the error scenarios are handled as well.

