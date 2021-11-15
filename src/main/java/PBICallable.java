import java.util.concurrent.Callable;

public interface PBICallable extends Callable<Void> {

	@Override
	default Void call() throws Exception {
		try {
			run();
		}
		catch(Exception e) {
			System.err.println("ERROR in "+getBlogId());
			e.printStackTrace();
			throw e;
		}
		return null;
	}

	String getBlogId();

	void run() throws Exception;
}
