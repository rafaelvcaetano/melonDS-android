package me.magnum.melonds.domain.model.retroachievements.exception

class RAGameNotExist(gameHash: String) : Exception("There is no game for hash $gameHash")