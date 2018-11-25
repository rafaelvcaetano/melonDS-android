package me.magnum.melonds;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

public class InternalRomLocationCache implements IRomLocationCache {
	private static final String CACHE_FILE = "rompaths.txt";

	private Context context;

	public InternalRomLocationCache(Context context) {
		this.context = context;
	}

	@Override
	public Completable cacheRomPaths(final List<String> paths) {
		return Completable.create(new CompletableOnSubscribe() {
			@Override
			public void subscribe(CompletableEmitter emitter) throws Exception {
				File cacheFile = new File(context.getCacheDir(), CACHE_FILE);
				FileWriter writer = null;

				try {
					writer = new FileWriter(cacheFile);

					for (String path : paths) {
						writer.write(path);
						writer.write('\n');
					}
				} catch (Exception e) {
					throw e;
				} finally {
					if (writer != null) {
						writer.close();
					}
				}
				emitter.onComplete();
			}
		});
	}

	@Override
	public Observable<String> getCachedRomPaths() {
		return Observable.create(new ObservableOnSubscribe<String>() {
			@Override
			public void subscribe(ObservableEmitter<String> emitter) throws Exception {
				File cacheFile = new File(context.getCacheDir(), CACHE_FILE);
				if (!cacheFile.isFile()) {
					emitter.onComplete();
					return;
				}

				FileInputStream fileStream = new FileInputStream(cacheFile);
				BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream));
				String line;
				while ((line = reader.readLine()) != null) {
					emitter.onNext(line);
				}
				emitter.onComplete();
				reader.close();
			}
		});
	}
}
