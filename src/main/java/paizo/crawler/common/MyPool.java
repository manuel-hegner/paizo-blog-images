package paizo.crawler.common;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.HttpStatusException;

import com.google.common.util.concurrent.Uninterruptibles;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MyPool {

	private final String name;
	private final ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));
	private final AtomicInteger submitted = new AtomicInteger();
	private final AtomicInteger done = new AtomicInteger();

	public <T> Future<T> submit(Callable<T> job) {
		submitted.incrementAndGet();
		return pool.submit(new Callable<T>() {
			@Override
			public T call() throws Exception {
				try {
					return execute();
				} finally {
					done.incrementAndGet();
				}
			}

            private T execute() {
                try {
                    while(true) {
                        try {
                            return job.call();
                        } catch(HttpStatusException e) {
                            if(e.getStatusCode() == 429) { //too many requests
                                Uninterruptibles.sleepUninterruptibly(5, TimeUnit.MINUTES);
                            }
                            else {
                                throw e;
                            }
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
		});
	}

	public void shutdown() throws InterruptedException {
		pool.shutdown();
		System.out.println(name+": submitted "+submitted.get()+" tasks");
		while(!pool.isTerminated()) {
			Thread.sleep(1000);
			System.out.println(name+": \tdone "+done.get()+"/"+submitted.get());
		}
	}

}
