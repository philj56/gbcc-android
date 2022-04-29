/*
 * Copyright (C) 2019-2020 Philip Jones
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

package com.philj56.gbcc

import android.os.Bundle
import androidx.appcompat.widget.SwitchCompat
import com.philj56.gbcc.cheat.Cheat
import com.philj56.gbcc.cheat.CheatAdapter
import com.philj56.gbcc.cheat.CheatDialogFragment
import com.philj56.gbcc.databinding.ActivityCheatListBinding
import java.io.File

class CheatActivity : BaseActivity() {
    private lateinit var configFile: File
    private lateinit var adapter: CheatAdapter
    private var cheats = ArrayList<Cheat>()
    private lateinit var binding: ActivityCheatListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheatListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val configDir = filesDir.resolve("config")
        configDir.mkdirs()
        val bundle = intent.extras
        val filename = File(bundle?.getString("file") ?: "").nameWithoutExtension
        configFile = configDir.resolve("$filename.cheats")

        if (configFile.exists()) {
            loadFile()
        } else {
            configFile.createNewFile()
        }

        binding.floatingActionButton.setOnClickListener { showCheatDialog() }

        adapter = CheatAdapter(this, R.layout.entry_cheat, R.id.cheatDescription, cheats)
        binding.cheatList.adapter = adapter

        binding.cheatList.setOnItemClickListener { _, view, position, _ ->
            cheats[position].active = !cheats[position].active
            view.findViewById<SwitchCompat>(R.id.cheatActive).isChecked = cheats[position].active
        }

        binding.cheatList.setOnItemLongClickListener { _, _, position, _ ->
            val dialog = CheatDialogFragment(position)
            dialog.isCancelable = false
            dialog.show(supportFragmentManager, "")
            return@setOnItemLongClickListener true
        }
    }

    override fun onDestroy() {
        saveFile()
        super.onDestroy()
    }

    fun clearConfig() {
        configFile.delete()
        finish()
    }

    private fun showCheatDialog() {
        val dialog = CheatDialogFragment(-1)
        dialog.isCancelable = false
        dialog.show(supportFragmentManager, "")
    }

    fun addCheat(cheat: Cheat, index: Int) {
        if (index >= 0) {
            cheats[index] = cheat
        } else {
            cheats.add(cheat)
        }
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    fun removeCheat(index: Int) {
        cheats.removeAt(index)
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    fun getCheat(index: Int): Cheat {
        return cheats[index]
    }

    private fun saveFile() {
        configFile.outputStream().use {
            cheats.forEach { (description, code, active) ->
                if (!active) {
                    it.write("#".toByteArray())
                }
                it.write("cheat=$code#$description\n".toByteArray())
            }
        }
    }

    private fun loadFile() {
        configFile.forEachLine { line ->
            val active = !line.startsWith("#")
            val (_, code, description) =
                if (!active) { line.substring(1) } else { line }
                    .split("=", "#")
            cheats.add(Cheat(description, code, active))
        }
    }
}

