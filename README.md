[![CircleCI](https://circleci.com/gh/echsylon/blocks-network.svg?style=shield)](https://circleci.com/gh/echsylon/blocks-network) [![Coverage Status](https://coveralls.io/repos/github/echsylon/blocks-network/badge.svg)](https://coveralls.io/github/echsylon/blocks-network) [![JitPack Snapshot](https://jitpack.io/v/echsylon/blocks-network.svg)](https://jitpack.io/#echsylon/blocks-network) [![Download](https://api.bintray.com/packages/echsylon/maven/network/images/download.svg)](https://bintray.com/echsylon/maven/network/_latestVersion)

## Network
This is a simple callback infrastructure. It will allow you to define "tasks" and attach three different types of optional callback listeners; SuccessListener, ErrorListener and FinishListener. The library also offers a CallbackManager impelementation to manage the attached listeners for you and, when the time is right, deliver the result of the task through them.

## Include
Add below gradle dependencies in your module build script like so:

```groovy
dependencies {
    compile 'com.echsylon.blocks:network:{version}'
}
```

## The `JsonNetworkRequest` implementation: Seperation of concerns

This library will enable the below asynchronous network request:

```java
public class MyActivity extends AppCompatActivity {

    @Override
    public void onResume() {
        super.onResume();

        showProgressDialog();
        Product.get(123L)
            .withFinishListener(this::hideProgressDialog)
            .withErrorListener(this::showError)
            .withSuccessListener(this::parseJson);
    }

    private void hideProgressDialog() {
        // Hide the progress dialog
    }

    private void showError(Throwable cause) {
        // Show an error snackbar
    }

    private void parseJson(String json) {
        // Parse the JSON and populate UI
    }
}
```

For this to work you'll have to implement your `Product` domain. It could look something like this:

```java
public class Product {

    public static class DTO {
        public final long id;
        public final float price;
        public final String name;
        
        public DTO(int id, float price, String name) {
            this.id = id;
            this.price = price;
            this.name = name;
        }
    }
    
    
    public Request<Product.DTO> get(long id) {
        return JsonNetworkRequest.enqueue(
                DefaultNetworkClient.getInstance(),
                "http://api.project.com/path/to/resource?id=123",
                "GET",
                null, // headers
                null, // payload
                new DefaultJsonParser(),
                Product.DTO.class);
    }
}
```

## The `DefaultNetworkClien`
The core of this library is the default `NetworkClient` implementation. Internally it relies on `OkHttp`. The choice of a singleton design pattern is open for debate, but [that's how the `OkHttp` client seems to work optimally](https://github.com/square/okhttp/blob/master/okhttp/src/main/java/okhttp3/OkHttpClient.java).

You can, of course, provide your own `NetworkClient` implementation if the default one doesn't fit your needs.
