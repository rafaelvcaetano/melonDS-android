package me.magnum.melonds;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;

public interface IRomLocationCache {
	Completable cacheRomPaths(List<String> paths);
	Observable<String> getCachedRomPaths();
}
