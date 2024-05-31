require 'active_record'

class Image < ActiveRecord::Base
  self.table_name = 'images'
  validates :name, presence: true, uniqueness: true
  has_many :detections, dependent: :destroy
  has_many :tags, through: :detections
end

class Tag < ActiveRecord::Base
  self.table_name = 'tags'
  validates :name, presence: true, uniqueness: true
  has_many :detections, dependent: :destroy
  has_many :images, through: :detections
end

class Detection < ActiveRecord::Base
  self.table_name = 'detections'
  validates :image_id, :tag_id, :x_min, :y_min, :x_max, :y_max, presence: true
  belongs_to :image
  belongs_to :tag
end

# CRUD functions
def add_image(name)
  image = Image.create(name: name)

  if image.persisted?
    puts "Image #{name} added"
  else
    puts "Error: Image #{name} already exists"
  end

  image
end

def delete_all_images
  Image.delete_all
  puts "All images deleted"
end

def add_tag(name)
  tag = Tag.create(name: name)

  if tag.persisted?
    puts "Tag #{name} added"
  else
    puts "Error: Tag #{name} already exists"
  end

  tag
end

def delete_all_tags
  Image.delete_all
  puts "All images deleted"
end

def add_detection(image_name, tag_name, x_min, y_min, x_max, y_max)
  image = Image.find_or_create_by(name: image_name)
  tag = Tag.find_or_create_by(name: tag_name)

  if image.persisted? && tag.persisted?
    detection = Detection.create(
      image_id: image.id,
      tag_id: tag.id,
      x_min: x_min,
      y_min: y_min,
      x_max: x_max,
      y_max: y_max
    )

    if detection.persisted?
      puts "Detection of tag #{tag_name} for image #{image_name} added"
      detection
    else
      puts "Error: Could not add detection of tag #{tag_name} for image #{image_name}"
      nil
    end
  else
    puts "Error: Could not find or create image or tag"
    nil
  end
end

def delete_all_tables
  tables = ActiveRecord::Base.connection.tables - %w[schema_migrations ar_internal_metadata]
  tables.each do |table|
    ActiveRecord::Base.connection.execute("DROP TABLE IF EXISTS #{table} CASCADE")
    puts "Table '#{table}' has been dropped."
  end
end