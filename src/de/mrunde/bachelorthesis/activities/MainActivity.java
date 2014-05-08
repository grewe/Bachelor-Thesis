package de.mrunde.bachelorthesis.activities;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.mapquest.android.maps.BoundingBox;
import com.mapquest.android.maps.DefaultItemizedOverlay;
import com.mapquest.android.maps.GeoPoint;
import com.mapquest.android.maps.MapActivity;
import com.mapquest.android.maps.MapView;
import com.mapquest.android.maps.MyLocationOverlay;
import com.mapquest.android.maps.OverlayItem;
import com.mapquest.android.maps.RouteManager;
import com.mapquest.android.maps.RouteResponse;

import de.mrunde.bachelorthesis.R;

/**
 * This is the initial activity which is started with the application. It offers
 * the user to change the route type and to search for his desired destination.
 * 
 * @author Marius Runde
 */
public class MainActivity extends MapActivity implements OnInitListener {

	/**
	 * Maximum amount of results for the destination
	 */
	private final int MAX_RESULTS = 5;

	// --- Route types ---
	/**
	 * Fastest route type
	 */
	private final String ROUTETYPE_FASTEST = "fastest";

	/**
	 * Shortest route type
	 */
	private final String ROUTETYPE_SHORTEST = "shortest";

	/**
	 * Pedestrian route type
	 */
	private final String ROUTETYPE_PEDESTRIAN = "pedestrian";

	/**
	 * Bicycle route type
	 */
	private final String ROUTETYPE_BICYCLE = "bicycle";

	/**
	 * Current route type
	 */
	private String routeType;

	// --- End of route types ---

	// --- The graphical user interface (GUI) ---
	/**
	 * The entered destination
	 */
	private EditText edt_destination;

	/**
	 * The "search for destination" button
	 */
	private Button btn_search;

	/**
	 * The "calculate route" button<br/>
	 * This button also starts the navigation after route calculation.
	 */
	private Button btn_calculate;

	/**
	 * The initial map view
	 */
	protected MapView map;

	/**
	 * An overlay to display the user's location
	 */
	private MyLocationOverlay myLocationOverlay;

	// --- End of graphical user interface ---

	/**
	 * Route manager for route calculation
	 */
	private RouteManager rm;

	/**
	 * The current location as a String
	 */
	private String str_currentLocation;

	/**
	 * The destination as a String
	 */
	private String str_destination;

	/**
	 * Coordinates of the destination to be sent to the NaviActivity
	 */
	private double[] destination_coords;

	/**
	 * TextToSpeech for audio output
	 */
	private TextToSpeech tts;

	/**
	 * This method is called when the application has been started
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Initialize the TextToSpeech
		tts = new TextToSpeech(this, this);

		// Set the route type to fastest
		this.routeType = ROUTETYPE_FASTEST;

		// Setup the whole GUI and map
		setupGUI();
		setupMapView();
		setupMyLocation();
	}

	/**
	 * Set up the GUI
	 */
	private void setupGUI() {
		this.edt_destination = (EditText) findViewById(R.id.edt_destination);
		edt_destination.setText("Berlin"); // TODO just for testing, must be
											// deleted...

		this.btn_search = (Button) findViewById(R.id.btn_search);
		btn_search.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// Get the entered destination
				str_destination = edt_destination.getText().toString();

				if (edt_destination.getText().toString().length() == 0) {
					Toast.makeText(MainActivity.this,
							R.string.noDestinationEntered, Toast.LENGTH_SHORT)
							.show();
				} else {
					List<Address> addresses;
					try {
						// Create a geocoder to locate the destination
						Geocoder geocoder = new Geocoder(MainActivity.this,
								Locale.getDefault());
						addresses = geocoder.getFromLocationName(
								str_destination, MAX_RESULTS);
					} catch (IOException e) {
						// Destination could not be located
						Log.e("MainActivity",
								"IO Exception in searching for destination");
						Toast.makeText(MainActivity.this,
								R.string.noDestinationFound, Toast.LENGTH_SHORT)
								.show();
						return;
					}

					if (addresses.isEmpty()) {
						// Destination could not be located
						Toast.makeText(MainActivity.this,
								R.string.noDestinationFound, Toast.LENGTH_SHORT)
								.show();
						return;
					}

					// Get the lat/lon values of the destination for the
					// destination overlay
					double lat = addresses.get(0).getLatitude();
					double lng = addresses.get(0).getLongitude();

					// Create the destination overlay
					addDestinationOverlay(lat, lng);

					// If the route has been calculated before change the text
					// of the button so the route has to be calculated again and
					// clear the route from the RouteManager
					if (btn_calculate.getText() == getResources().getString(
							R.string.start)) {
						btn_calculate.setText(R.string.calculate);
						rm.clearRoute();
					}
				}
			}
		});

		this.btn_calculate = (Button) findViewById(R.id.btn_calculate);
		btn_calculate.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (str_destination == null) {
					Toast.makeText(MainActivity.this,
							R.string.noDestinationEntered, Toast.LENGTH_SHORT)
							.show();
				} else if (btn_calculate.getText() == getResources().getString(
						R.string.calculate)) {
					// Inform the user about the route is being calculated
					tts.speak("Calculating route from current location to "
							+ str_destination, TextToSpeech.QUEUE_FLUSH, null);

					// Transform the current location into a String
					str_currentLocation = "{latLng:{lat:"
							+ myLocationOverlay.getMyLocation().getLatitude()
							+ ",lng:"
							+ myLocationOverlay.getMyLocation().getLongitude()
							+ "}}";

					// Calculate the route
					calculateRoute();
				} else {
					// Create an Intent to start the NaviActivity and hereby the
					// navigation
					Intent intent = new Intent(MainActivity.this,
							NaviActivity.class);
					intent.putExtra("str_currentLocation", str_currentLocation);
					intent.putExtra("str_destination", str_destination);
					intent.putExtra("destination_lat", destination_coords[0]);
					intent.putExtra("destination_lng", destination_coords[1]);
					intent.putExtra("routeOptions", getRouteOptions());
					startActivity(intent);
				}
			}
		});
	}

	/**
	 * Add the destination overlay to the map
	 * 
	 * @param lat
	 *            Latitude of the destination
	 * @param lng
	 *            Longitude of the destination
	 */
	private void addDestinationOverlay(double lat, double lng) {
		// Create a GeoPoint object of the current location and the destination
		GeoPoint currentLocation = new GeoPoint(myLocationOverlay
				.getMyLocation().getLatitude(), myLocationOverlay
				.getMyLocation().getLongitude());
		GeoPoint destination = new GeoPoint(lat, lng);

		// Also set the coordinates of the destination for the NaviActivity
		this.destination_coords = new double[] { lat, lng };

		// Clear previous overlays first
		if (map.getOverlays().size() > 1) {
			map.getOverlays().remove(1);
		}

		// Create the destination overlay
		OverlayItem oi_destination = new OverlayItem(destination,
				"Destination", str_destination);
		final DefaultItemizedOverlay destinationOverlay = new DefaultItemizedOverlay(
				getResources().getDrawable(R.drawable.destination_flag));
		destinationOverlay.addItem(oi_destination);

		// Add the overlay to the map
		map.getOverlays().add(destinationOverlay);

		// Zoom and pan the map to show all overlays
		map.getController().zoomToSpan(
				new BoundingBox(currentLocation, destination));
	}

	/**
	 * Calculate the route from the current location to the destination
	 */
	private void calculateRoute() {
		// Clear the previous route first
		if (rm != null) {
			rm.clearRoute();
		}

		// Initialize a new RouteManager to calculate the route
		rm = new RouteManager(getBaseContext(), getResources().getString(
				R.string.apiKey));
		rm.setMapView(map);
		// Zoom and center the map to display the route
		rm.setBestFitRoute(true);
		// Set the route options (e.g. route type)
		rm.setOptions(getRouteOptions());
		// Set route callback
		rm.setRouteCallback(new RouteManager.RouteCallback() {

			@Override
			public void onSuccess(RouteResponse response) {
				// Route has been calculated successfully
				Log.i("MainActivity",
						getResources().getString(R.string.routeCalculated));
				// Change the text of the button to enable navigation
				btn_calculate.setText(R.string.start);
			}

			@Override
			public void onError(RouteResponse response) {
				// Route could not be calculated
				Log.e("MainActivity",
						getResources().getString(R.string.routeNotCalculated));
			}
		});
		// Calculate the route and display it on the map
		rm.createRoute(str_currentLocation, str_destination);
	}

	/**
	 * Setup the route options and return them
	 * 
	 * @return Route options as String
	 */
	private String getRouteOptions() {
		JSONObject options = new JSONObject();

		try {
			// Set the units to kilometers
			String unit = "m";
			options.put("unit", unit);

			// Set the route type
			options.put("routeType", routeType);

			// Set the output shape format
			String outShapeFormat = "raw";
			options.put("outShapeFormat", outShapeFormat);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return options.toString();
	}

	/**
	 * Set up the map and enable default zoom controls
	 */
	private void setupMapView() {
		this.map = (MapView) findViewById(R.id.map);
		map.setBuiltInZoomControls(true);
	}

	/**
	 * Set up a MyLocationOverlay and execute the runnable once a location has
	 * been fixed
	 */
	private void setupMyLocation() {
		// Check if the GPS is enabled
		if (!((LocationManager) getSystemService(LOCATION_SERVICE))
				.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			// Open the location settings if it is disabled
			Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivity(intent);
		}

		// Create the MyLocationOverlay
		this.myLocationOverlay = new MyLocationOverlay(this, map);
		myLocationOverlay.enableMyLocation();
		myLocationOverlay.runOnFirstFix(new Runnable() {

			@Override
			public void run() {
				GeoPoint currentLocation = myLocationOverlay.getMyLocation();
				map.getController().animateTo(currentLocation);
				map.getController().setZoom(14);
				map.getOverlays().add(myLocationOverlay);
				myLocationOverlay.setFollowing(true);
			}
		});
	}

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * Called when the OptionsMenu is created
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * Called when an item of the OptionsMenu is clicked
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Initialize an AlertDialog.Builder and an AlertDialog
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		AlertDialog dialog;

		// Handle item selection
		switch (item.getItemId()) {
		case R.id.about:
			// Inform the user about this application
			builder.setMessage("This is the Bachelor Thesis of Marius Runde");
			builder.setPositiveButton("Awesome!",
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							// User clicked the "Awesome!" button
						}
					});
			dialog = builder.create();
			dialog.show();
			return true;
		case R.id.help:
			// Inform the user about this application
			builder.setMessage("Coming soon...");
			builder.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							// User clicked the "OK" button
						}
					});
			dialog = builder.create();
			dialog.show();
			return true;
		case R.id.settings:
			// Change the route type in the settings
			builder.setTitle(R.string.routeType);
			builder.setItems(R.array.routeTypes,
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
							case 0:
								// Fastest selected
								routeType = ROUTETYPE_FASTEST;
								Toast.makeText(MainActivity.this,
										"Fastest route type selected",
										Toast.LENGTH_SHORT).show();
								break;
							case 1:
								// Shortest selected
								routeType = ROUTETYPE_SHORTEST;
								Toast.makeText(MainActivity.this,
										"Shortest route type selected",
										Toast.LENGTH_SHORT).show();
								break;
							case 2:
								// Pedestrian selected
								routeType = ROUTETYPE_PEDESTRIAN;
								Toast.makeText(MainActivity.this,
										"Pedestrian route type selected",
										Toast.LENGTH_SHORT).show();
								break;
							case 3:
								// Bicycle selected
								routeType = ROUTETYPE_BICYCLE;
								Toast.makeText(MainActivity.this,
										"Bicycle route type selected",
										Toast.LENGTH_SHORT).show();
								break;
							default:
								break;
							}
						}
					});
			dialog = builder.create();
			dialog.show();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Enable features of the MyLocationOverlay
	 */
	@Override
	protected void onResume() {
		myLocationOverlay.enableMyLocation();
		super.onResume();
	}

	/**
	 * Disable features of the MyLocationOverlay when in the background
	 */
	@Override
	protected void onPause() {
		super.onPause();
		myLocationOverlay.disableMyLocation();
	}

	/**
	 * Shut down the TextToSpeech engine when the application is terminated
	 */
	@Override
	protected void onDestroy() {
		if (tts != null) {
			tts.stop();
			tts.shutdown();
		}
		super.onDestroy();
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			tts.setLanguage(Locale.ENGLISH);
		} else {
			tts = null;
			Log.e("MainActivity", "Failed to initialize the TextToSpeech");
		}
	}
}