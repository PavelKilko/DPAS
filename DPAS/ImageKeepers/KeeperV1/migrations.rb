require 'active_record'

class CreateImages < ActiveRecord::Migration[7.1]
  def change
    create_table :images do |t|
      t.string :name, null: false

      t.timestamps
    end

    add_index :images, :name, unique: true
  end
end

class CreateTags < ActiveRecord::Migration[7.1]
  def change
    create_table :tags do |t|
      t.string :name, null: false

      t.timestamps
    end

    add_index :tags, :name, unique: true
  end
end

class CreateDetections < ActiveRecord::Migration[7.1]
  def change
    create_table :detections do |t|
      t.references :image, null: false, foreign_key: { on_delete: :cascade }
      t.references :tag, null: false, foreign_key: { on_delete: :cascade }
      t.integer :x_min, null: false
      t.integer :y_min, null: false
      t.integer :x_max, null: false
      t.integer :y_max, null: false

      t.timestamps
    end

    add_index :detections, [:image_id, :tag_id]
  end
end

def run_migrations
  migrations = {
    images: CreateImages,
    tags: CreateTags,
    detections: CreateDetections
  }

  migrations.each do |table_name, migration|
    if ActiveRecord::Base.connection.table_exists?(table_name)
      puts "Table #{table_name} already exists"
    else
      migration.migrate(:up)
      puts "Table #{table_name} created."
    end
  end
end