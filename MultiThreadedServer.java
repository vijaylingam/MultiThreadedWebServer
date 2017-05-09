/**
	Author: Vijay Lingam
	Date: April 18, 2017
	Project: MultiThreaded WebServer
*/

// Import statements for the project	
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Scanner;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.io.FileInputStream;
import java.util.Date;

/**
	HttpRequest Class is invoked by the Connection Class. 
	A minimal Http request looks as follows:
		GET / HTTP/1.1
		Host: www.google.com
	This class has 4 methods.
	This method is an adaptation from: https://twistedmatrix.com/documents/14.0.0/core/howto/application.html
*/
class HttpRequest {
	private String method;
	private String url; // universal resource locator: cantains domain + port + path
	private String protocol; // HTTP/1.1 protocol 
	private NavigableMap<String, String> headers = new TreeMap<String, String>();
	private List<String> body = new ArrayList<String>();

	// Class constructor
	private HttpRequest(){}
/**
	parseAsHttp() method takes in an argument 'in' of type InputStream and returns an object of HttpRequest.
*/
	public static HttpRequest parseAsHttp(InputStream in) {
		try {
			HttpRequest request = new HttpRequest(); //creates a new Http request
			BufferedReader reader = new BufferedReader(new InputStreamReader(in)); 
			String line = reader.readLine(); // read request
			if (line == null) // error handling
				throw new IOException("Server accepts only HTTP requests.");
			String[] requestLine = line.split(" ", 3);
			if (requestLine.length != 3) { // request line has 3 parts: request method: GET/POST etc, protocol: HTTP/1.1, and Host.
				throw new IOException("Cannot parse request line from \"" + line + "\"");
			}
			if (!requestLine[2].startsWith("HTTP/")) {
				throw new IOException("Server accepts only HTTP requests.");
			}
			request.method = requestLine[0];
			request.url = requestLine[1];
			request.protocol = requestLine[2];
			// parse request
			line = reader.readLine();
			while(line != null && !line.equals("")) {
				String[] header = line.split(": ", 2);
				if (header.length != 2)
					throw new IOException("Cannot parse header from \"" + line + "\"");
				else 
					request.headers.put(header[0], header[1]);
				line = reader.readLine();
			}
			while(reader.ready()) {
				line = reader.readLine();
				request.body.add(line);
			}
			return request;
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
		return null;
	}
/**
	getMethod() returns either GET or HEAD
*/
	public String getMethod() {
		return method;
	}
	
	public String getUrl() {
		return url;
	}
	// overriding inbuilt toString() method. This overrided method is used to convert date to a string and concatenate
	// various elements using colons. 
	@Override public String toString() {
		String result = method + " " + url + " " + protocol + "\n";
		for (String key : headers.keySet()) {
			result += key + ": " + headers.get(key) + "\n";
		}
		result += "\r\n";
		for (String line : body) {
			result += line + "\n"; 
		}
		return result;
	}
	
	public static class HttpMethod {
		public static final String GET = "GET";
		public static final String HEAD = "HEAD";
	}
 }

/**
	HttpResponse takes a request and returns a response. A response might look as below:
		HTTP/1.1 200 OK
		Content-Type: text/plain
		Content-Length: 17
		Connection: Close

		Hello HTTP world!
	Response contains header lines followed by a blank line and then followed by body/message.
	This class is based on and is an adapdation of the Response Class from these sources: 
		a) http://www.cs.bu.edu/fac/matta/Teaching/CS552/F99/proj4/
		b) http://www.iith.ac.in/~tbr/teaching/lab1.html
	HttpResponse Class has 9 methods.	
*/	
class HttpResponse {
	// Using HTTP/1.0 protocol for request and response 
	private static final String protocol = "HTTP/1.0"; 
	private String status; //page status representing error codes
	private NavigableMap<String, String> headers = new TreeMap<String, String>(); //saves header files as illustrated above
	private byte[] body = null; //message of the response is set to null

	// HttpResponse Constructor
	public HttpResponse(String status) {
		this.status = status;
		setDate(new Date());
	}

	/**
		withFile() method takes in the argument 'f' of type File and has a return type HttpResponse.
		This method looks for the file that is being requested and return the message part of the html by processing it.
		An appropriate error will get generated if the requested file does not exist.
	*/
	public HttpResponse withFile(File f) {
		if (f.isFile()){
			try {
				// read the file f
				FileInputStream reader = new FileInputStream(f);
				int length = reader.available();
				body = new byte[length];
				reader.read(body);
				reader.close();
				
				setContentLength(length);
				// check if the file has an extension htm or html
				if(f.getName().endsWith(".htm") || f.getName().endsWith(".html")){
					setContentType(ContentType.HTML);
				} 
				else{
					setContentType(ContentType.TEXT);
				}
			}catch (IOException e){ //error handling in case the file is corrupt
				System.err.println("Error while reading " + f);
			}
			return this; //return the HttpResponse object
		} else {
			return new HttpResponse(StatusCode.NOT_FOUND) //return 404 error: Page Not Found
				.withHtmlBody("<html><body>File " + f + " not found.</body></html>");
		}
	}

	/**
		withHtmlBody() method takes in an argument 'msg' of type String and return an object of type HttpResponse.
		This method sets the body variable to the content (msg).
	*/
	public HttpResponse withHtmlBody(String msg) {
		setContentLength(msg.getBytes().length);
		setContentType(ContentType.HTML);
		body = msg.getBytes();
		return this;
	}
	/**
		setDate() method takes in an argument 'date' of type Date and returns nothing, hence the return type void.
		This method adds the date to the header file of Response.
	*/
	public void setDate(Date date) {
		headers.put("Date", date.toString());
	}
	/**
		setContentLength() method takes in an argument 'value' of type long and returns nothing. 
		This method adds a header line specifying Content Length as illustrated above.
	*/
	public void setContentLength(long value) {
		headers.put("Content-Length", String.valueOf(value));
	}
	/**
		setContentType() method takes in an argument 'value' of type String and returns nothing.
		This method adds a header line which specifies the content-type of the file: text/plain or text/html
	*/
	public void setContentType(String value) {
		headers.put("Content-Type", value);
	}
	/**
		removeBody() method sets the body variable to null
	*/
	public void removeBody() {
		body = null;
	}
	// overriding inbuilt toString() method. This overrided method is used to convert date to a string and concatenate
	// various elements using colons. 
	@Override public String toString() {
		String result = protocol + " " + status +"\n";
		for (String key : headers.descendingKeySet()) {
			result += key + ": " + headers.get(key) + "\n";
		}
		result += "\r\n";
		if (body != null) {
			result += new String(body);
		}
		return result;
	}
	/**
	StatusCode Class contains different String Constants which can be substituted for corresponding error codes.
	This is useful for setting up the Http Response
	*/
	public static class StatusCode {
		public static final String OK = "200 OK";
		public static final String NOT_FOUND = "404 Not Found";
		public static final String NOT_IMPLEMENTED = "501 Not Implemented";
	}
/**
	ContentType Class is used by setContentType() to substitute value with relevant string: TEXT/ HTML
*/
	public static class ContentType {
		public static final String TEXT = "text/plain";
		public static final String HTML = "text/html";
	}
 }
/**
	Connection Class establishes a connection with the socket(client) and mediates Http Request and Response. 
	This Class is invoked by the Main Class: MultiThreadedWebServer. 
	This Class contains one overrided: run and another method: respond
*/
class Connection implements Runnable {

	private MultiThreadedServer server;
	private Socket client;
	private InputStream in;
	private OutputStream out;

	// Class Constructor
	public Connection(Socket cl, MultiThreadedServer s){
		client = cl;
		server = s;
	}

	@Override public void run() {
		try {
			in = client.getInputStream();
			out = client.getOutputStream();

			// Input Request is of the form:
			//	GET / HTTP/1.1 Host: www.example.com
			
		 	HttpRequest request = HttpRequest.parseAsHttp(in);
			
			if(request != null){
				System.out.println("Request: " + request.getUrl() + " being processed " +
					"by socket at " + client.getInetAddress() +":"+ client.getPort());
				
				HttpResponse response; // variable response of type HttpResponse
				String method;
				// Fetch a page from root directory
				if((method = request.getMethod()).equals(HttpRequest.HttpMethod.GET) || method.equals(HttpRequest.HttpMethod.HEAD)){
					File f = new File(server.getWebRoot() + request.getUrl());
					// Status Code OK = File exists
					// response is of type HttpResponse
					response = new HttpResponse(HttpResponse.StatusCode.OK).withFile(f);
					if(method.equals(HttpRequest.HttpMethod.HEAD)){
						response.removeBody();
					}
				} 
				else{
					// If page does not exist
					response = new HttpResponse(HttpResponse.StatusCode.NOT_IMPLEMENTED);
				}
				respond(response);
				
			} 
			else{
				System.err.println("Server accepts only HTTP protocol.");
			}
			in.close(); // Close inputstream
			out.close(); // Close outputstream
		 } catch (IOException e) {
		 	System.err.println("Error in client's IO.");
		} finally {
			try {
				client.close();
			} catch (IOException e) {
				System.err.println("Error while closing client socket.");
			}
		}
	}
	/**
	respond method is of type void and takes in an argument of type HttpResponse and writes the appropriate error message.
	*/
	public void respond(HttpResponse response) {
		String toSend = response.toString();
		PrintWriter writer = new PrintWriter(out);
		writer.write(toSend);
		writer.flush();
	}

}
/**
	Main class for MultiThreaded Server.
	This Class spawns a new thread for every HTTP request and initializes variables for further processing.
*/
public class MultiThreadedServer implements Runnable {
	//These variables can only be accessed by this Class
	private ServerSocket myserver; 
	private final String rootDirectory; 
	private ExecutorService threadpool; 
	private final int port;
	private final int maxThreads;

	public static void main(String[] args) {
		Scanner in = new Scanner(System.in);
		System.out.printf("Enter port number: ");
		int port = Integer.parseInt(in.nextLine());
		System.out.printf("Enter root directory path: ");
		String rootdirectory = in.nextLine();
		System.out.printf("Enter Maximum number of Threads (for threadpooling): ");
		int max = Integer.parseInt(in.nextLine());
		// Start a new Thread for every http request
		new Thread(new MultiThreadedServer(port, rootdirectory, max)).start();
	}

	// constructor for MultiThreadedServer Clas
	public MultiThreadedServer(int port, String root, int max){ 
		this.port = port; // assigning port number
		this.maxThreads = max; // Maximum number of threads in threadpool
		this.rootDirectory = root; // Root Directory where http objects exist
	}
	/**
	Overriding the run method.
	This method creates a new thread executing the Http request and establishes a connection to the port.
	*/
	@Override public void run() {
		try {
			// create a new object myserver of type ServerSocket
			myserver = new ServerSocket(port);
			// limit the number of threads to maxThreads
			threadpool = Executors.newFixedThreadPool(maxThreads);
		}
		// Checks if the port number can be assigned  
		catch(IOException e){
			System.err.println("Port number: " + port + " cannot be used.");
			System.exit(0);
		}
		
		System.out.println("Starting server on port: " + port + " with root folder \"" + rootDirectory + "\" and " + maxThreads + " threads limit.");
		while (!Thread.interrupted()){ // Condition remains true until the user interrupts using ctrl+d or an equivalent signal
			try {
				// establish a new connection
				threadpool.execute(new Thread(new Connection(myserver.accept(), this))); // new object of type Connection
			} catch (IOException e) {
				System.err.println("Error accepting request.");
			}
		}
		endserver();
	}
	/**
	Close the server and kill all active threads and free up resources used my threads.
	This method is of type void and doesn't take any input parameters.
	*/
	public void endserver() {
		try {
			myserver.close();
		} catch (IOException e) {
			System.err.println("Unexpected error while trying to closing server socket.");
		}
		// free all thread memory usage and kill them
		threadpool.shutdown();
		try {
			if (!threadpool.awaitTermination(5, TimeUnit.SECONDS)) //wait for 5 seconds and terminate all threads
				threadpool.shutdownNow();
		} catch (InterruptedException e) {
			System.out.println("Error while trying to kill threads.");
		}
	}
	/**
	returns Root directory
	*/
	public String getWebRoot() {
		return rootDirectory;
	}
}